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
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.codec.GridHash;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.channel.ChannelDirectory;
import io.kamax.grid.gridepo.core.channel.ChannelManager;
import io.kamax.grid.gridepo.core.crypto.Cryptopher;
import io.kamax.grid.gridepo.core.crypto.ed25519.Ed25519Cryptopher;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.event.EventStreamer;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.federation.FederationPusher;
import io.kamax.grid.gridepo.core.identity.IdentityManager;
import io.kamax.grid.gridepo.core.signal.AppStopping;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.core.store.UserDao;
import io.kamax.grid.gridepo.core.store.crypto.FileKeyStore;
import io.kamax.grid.gridepo.core.store.crypto.KeyStore;
import io.kamax.grid.gridepo.core.store.crypto.MemoryKeyStore;
import io.kamax.grid.gridepo.core.store.postgres.PostgreSQLStore;
import io.kamax.grid.gridepo.exception.InvalidTokenException;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MonolithGridepo implements Gridepo {

    private static final Logger log = KxLog.make(MonolithGridepo.class);

    private final ServerID origin;
    private final Algorithm jwtAlgo;
    private final JWTVerifier jwtVerifier;

    private GridepoConfig cfg;
    private SignalBus bus;
    private Store store;
    private KeyStore kStore;
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

        if (StringUtils.isBlank(cfg.getDomain())) {
            throw new RuntimeException("Configuration: domain cannot be blank");
        }
        origin = ServerID.from(cfg.getDomain());

        bus = new SignalBus();

        // FIXME use ServiceLoader
        String dbStoreType = cfg.getStorage().getDatabase().getType();
        if (StringUtils.equals("memory", dbStoreType)) {
            store = new MemoryStore();
        } else if (StringUtils.equals("postgresql", dbStoreType)) {
            store = new PostgreSQLStore(cfg.getStorage());
        } else {
            throw new IllegalArgumentException("Unknown database type: " + dbStoreType);
        }

        //FIXME use ServiceLoader
        String kStoreType = cfg.getStorage().getKey().getType();
        if (StringUtils.equals("file", kStoreType)) {
            kStore = new FileKeyStore(cfg.getStorage().getKey().getLocation());
        } else if (StringUtils.equals("memory", kStoreType)) {
            kStore = new MemoryKeyStore();
        } else {
            throw new IllegalArgumentException("Unknown keys storage: " + kStoreType);
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

        idMgr = new IdentityManager(cfg.getIdentity(), store);
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
    public UserSession login(String username, String password) {
        UserDao dao = idMgr.login(username, password);
        UserID uId = UserID.from(dao.getUsername(), cfg.getDomain());
        User u = new User(uId, dao.getUsername());

        String token = JWT.create()
                .withIssuer(cfg.getDomain())
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.ofEpochMilli(Long.MAX_VALUE)))
                .withClaim("UserID", uId.full())
                .withClaim("Username", dao.getUsername())
                .sign(jwtAlgo);

        store.insertUserAccessToken(dao.getLid(), token);
        tokens.put(token, true);
        return new UserSession(this, u, token);
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
            UserID uId = UserID.parse(data.getClaim("UserID").asString());
            String username = data.getClaim("Username").asString();
            return new UserSession(this, new User(uId, username), token);
        } catch (JWTVerificationException e) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    @Override
    public boolean isLocal(UserID uId) {
        return getDomain().equals(new String(Base64.decodeBase64(uId.base()), StandardCharsets.UTF_8).split("@", 2)[1]);
    }

    @Override
    public ServerSession forServer(String srvId) {
        return new ServerSession(this, ServerID.parse(srvId));
    }

    @Override
    public Store getStore() {
        return store;
    }

    @Override
    public IdentityManager getIdentity() {
        return idMgr;
    }

}
