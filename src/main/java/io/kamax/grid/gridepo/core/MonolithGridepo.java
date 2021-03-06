/*
 * Gridepo - Grid Data Server
 * Copyright (C) 2019 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.grid.gridepo.core;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.codec.GridHash;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.auth.AuthService;
import io.kamax.grid.gridepo.core.auth.Credentials;
import io.kamax.grid.gridepo.core.auth.UIAuthSession;
import io.kamax.grid.gridepo.core.auth.UIAuthStage;
import io.kamax.grid.gridepo.core.auth.multi.MultiStoreAuthService;
import io.kamax.grid.gridepo.core.channel.ChannelDirectory;
import io.kamax.grid.gridepo.core.channel.ChannelManager;
import io.kamax.grid.gridepo.core.crypto.Cryptopher;
import io.kamax.grid.gridepo.core.crypto.ed25519.Ed25519Cryptopher;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.event.EventStreamer;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.federation.FederationPusher;
import io.kamax.grid.gridepo.core.identity.GenericThreePid;
import io.kamax.grid.gridepo.core.identity.IdentityManager;
import io.kamax.grid.gridepo.core.identity.ThreePid;
import io.kamax.grid.gridepo.core.identity.User;
import io.kamax.grid.gridepo.core.signal.AppStopping;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.store.DataStore;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.core.store.crypto.FileKeyStore;
import io.kamax.grid.gridepo.core.store.crypto.KeyStore;
import io.kamax.grid.gridepo.core.store.crypto.MemoryKeyStore;
import io.kamax.grid.gridepo.core.store.postgres.PostgreSQLDataStore;
import io.kamax.grid.gridepo.exception.InternalServerError;
import io.kamax.grid.gridepo.exception.InvalidTokenException;
import io.kamax.grid.gridepo.exception.UnauthenticatedException;
import io.kamax.grid.gridepo.network.matrix.core.MatrixServer;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MonolithGridepo implements Gridepo {

    private static final Logger log = KxLog.make(MonolithGridepo.class);

    private final ServerID origin;
    private final Algorithm jwtAlgo;
    private final JWTVerifier jwtVerifier;

    private GridepoConfig cfg;
    private SignalBus bus;
    private DataStore store;
    private KeyStore kStore;
    private AuthService authSvc;
    private IdentityManager idMgr;
    private EventService evSvc;
    private ChannelManager chMgr;
    private ChannelDirectory chDir;
    private EventStreamer streamer;
    private DataServerManager dsMgr;
    private FederationPusher fedPush;

    private boolean isStopping;
    private Map<String, Boolean> tokens = new ConcurrentHashMap<>();

    public MonolithGridepo(GridepoConfig cfg) {
        this.cfg = cfg;
        if (cfg.getAuth().getFlows().isEmpty()) {
            cfg.getAuth().addFlow().addStage("g.auth.id.password");
        }

        if (StringUtils.isBlank(cfg.getDomain())) {
            throw new RuntimeException("Configuration: domain cannot be blank");
        }
        origin = ServerID.fromDns(cfg.getDomain());

        bus = new SignalBus();

        String kStoreType = cfg.getStorage().getKey().getType();
        String kStoreLoc = cfg.getStorage().getKey().getLocation();
        if (StringUtils.isBlank(kStoreLoc)) {
            kStoreLoc = Paths.get(cfg.getStorage().getData(), "keys").toString();
        }

        //FIXME use ServiceLoader
        if (StringUtils.equals("file", kStoreType)) {
            kStore = new FileKeyStore(kStoreLoc);
        } else if (StringUtils.equals("memory", kStoreType)) {
            kStore = new MemoryKeyStore();
        } else {
            throw new IllegalArgumentException("Unknown keys storage: " + kStoreType);
        }

        // FIXME use ServiceLoader
        String dbStoreType = cfg.getStorage().getDatabase().getType();
        if (StringUtils.equals("memory", dbStoreType)) {
            store = MemoryStore.get(cfg.getStorage().getDatabase().getConnection());
        } else if (StringUtils.equals("postgresql", dbStoreType)) {
            store = new PostgreSQLDataStore(cfg.getStorage());
        } else {
            throw new IllegalArgumentException("Unknown database type: " + dbStoreType);
        }

        dsMgr = new DataServerManager();
        Cryptopher crypto = new Ed25519Cryptopher(kStore);

        String jwtSeed = cfg.getCrypto().getSeed().get("jwt");
        if (StringUtils.isEmpty(jwtSeed)) {
            log.warn("JWT secret is not set, computing one from main signing key. Please set a JWT secret in your config");
            jwtSeed = GridHash.get().hashFromUtf8(cfg.getDomain() + crypto.getServerSigningKey().getPrivateKeyBase64());
        }

        jwtAlgo = Algorithm.HMAC256(jwtSeed);
        jwtVerifier = JWT.require(jwtAlgo)
                .withIssuer(cfg.getDomain())
                .build();

        evSvc = new EventService(origin, crypto);

        authSvc = new MultiStoreAuthService(cfg);
        idMgr = new IdentityManager(cfg.getIdentity(), store, crypto);
        chMgr = new ChannelManager(this, bus, evSvc, store, dsMgr);
        streamer = new EventStreamer(store);

        chDir = new ChannelDirectory(origin, store, bus, dsMgr);
        fedPush = new FederationPusher(this, dsMgr);

        log.info("We are {}", getDomain());
        log.info("Serving domain(s):");
        log.info("  - {}", origin.full());
    }

    @Override
    public void start() {
        isStopping = false;
    }

    @Override
    public void stop() {
        isStopping = true;

        bus.getMain().publish(AppStopping.Signal);

        fedPush.stop();
    }

    @Override
    public boolean isStopping() {
        return isStopping;
    }

    @Override
    public GridepoConfig getConfig() {
        return cfg;
    }

    @Override
    public String getDomain() {
        return cfg.getDomain();
    }

    @Override
    public ServerID getOrigin() {
        return origin;
    }

    @Override
    public boolean isLocal(ServerID sId) {
        return getOrigin().equals(sId);
    }

    @Override
    public SignalBus getBus() {
        return bus;
    }

    @Override
    public ChannelManager getChannelManager() {
        return chMgr;
    }

    @Override
    public ChannelDirectory getChannelDirectory() {
        return chDir;
    }

    @Override
    public EventService getEventService() {
        return evSvc;
    }

    @Override
    public EventStreamer getStreamer() {
        return streamer;
    }

    @Override
    public DataServerManager getServers() {
        return dsMgr;
    }

    @Override
    public FederationPusher getFedPusher() {
        return fedPush;
    }

    @Override
    public AuthService getAuth() {
        return authSvc;
    }

    @Override
    public UIAuthSession register() {
        return getAuth().getSession(getConfig().getAuth());
    }

    private UserSession createSession(User usr) {
        String token = JWT.create()
                .withIssuer(cfg.getDomain())
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.ofEpochMilli(Long.MAX_VALUE)))
                .withClaim(GridType.of("id.internal"), usr.getId())
                .sign(jwtAlgo);

        store.insertUserAccessToken(usr.getLid(), token);
        tokens.put(token, true);
        return new UserSession(this, usr, token);
    }

    @Override
    public UserSession register(String username, String password) {
        User user = getIdentity().createUserWithKey();
        user.addThreePid(new GenericThreePid(GridType.id().local().username(), username));
        store.addCredentials(user.getLid(), new Credentials(GridType.of("auth.id.password"), password));
        return createSession(user);
    }

    @Override
    public UIAuthSession login() {
        return getAuth().getSession(getConfig().getAuth());
    }

    @Override
    public UserSession login(UIAuthSession auth) {
        if (!auth.isAuthenticated()) {
            throw new UnauthenticatedException(auth);
        }

        Set<String> idStages = auth.getCompletedStages().stream()
                .filter(id -> id.startsWith(GridType.of("auth.id.")))
                .collect(Collectors.toSet());

        if (idStages.isEmpty()) {
            throw new InternalServerError("No ID-based authentication was completed, cannot identify the user");
        }

        // FIXME assumption that it will be done via password
        UIAuthStage stage = auth.getStage(GridType.of("auth.id.password"));

        ThreePid uid = stage.getUid();
        User usr = getIdentity().findUser(uid).orElseGet(() -> {
            User newUsr = idMgr.createUserWithKey();
            newUsr.linkToStoreId(uid);
            return newUsr;
        });

        return createSession(usr);
    }

    @Override
    public UserSession login(String username, String password) {
        UIAuthSession session = login();

        JsonObject id = new JsonObject();
        id.addProperty("type", GridType.id().local().username().getId());
        id.addProperty("value", username);
        JsonObject doc = new JsonObject();
        doc.addProperty("type", GridType.of("auth.id.password"));
        doc.addProperty("password", password);
        doc.add("identifier", id);

        session.complete(doc);
        return login(session);
    }

    @Override
    public UserSession login(JsonObject credentials) {
        Optional<String> sessionId = GsonUtil.findString(credentials, "session");
        UIAuthSession session;
        if (sessionId.isPresent()) {
            session = getAuth().getSession(sessionId.get());
        } else {
            session = login();
        }

        session.complete(credentials);
        return login(session);
    }

    @Override
    public void logout(UserSession session) {
        store.deleteUserAccessToken(session.getAccessToken());
        tokens.remove(session.getAccessToken());
    }

    @Override
    public UserSession withToken(String token) {
        if (!tokens.computeIfAbsent(token, t -> store.hasUserAccessToken(token))) {
            throw new InvalidTokenException("Unknown token");
        }

        try {
            DecodedJWT data = jwtVerifier.verify(JWT.decode(token));
            String uid = data.getClaim(GridType.of("id.internal")).asString();
            User user = getIdentity().getUser(uid); // FIXME check in cluster for missing events
            return new UserSession(this, user, token);
        } catch (JWTVerificationException e) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    @Override
    public boolean isLocal(UserID uId) {
        return idMgr.findUser(new GenericThreePid(GridType.of("id.net.grid"), uId.full())).isPresent();
    }

    @Override
    public ServerSession forServer(String srvId) {
        return new ServerSession(this, ServerID.parse(srvId));
    }

    @Override
    public MatrixServer forMatrix(String host) {
        return null;
    }

    @Override
    public DataStore getStore() {
        return store;
    }

    @Override
    public IdentityManager getIdentity() {
        return idMgr;
    }

}
