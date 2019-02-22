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
import com.auth0.jwt.interfaces.DecodedJWT;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.channel.ChannelManager;
import io.kamax.grid.gridepo.core.crypto.KeyManager;
import io.kamax.grid.gridepo.core.crypto.MemoryKeyStore;
import io.kamax.grid.gridepo.core.crypto.SignManager;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.event.EventStreamer;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.identity.IdentityManager;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.exception.InvalidTokenException;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.util.Base64;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MonolithGridepo implements Gridepo {

    private final Object syncLock = new Object();
    private boolean isStopping;

    private Algorithm jwtAlgo;
    private JWTVerifier jwtVerifier;

    private GridepoConfig cfg;
    private Store store;
    private IdentityManager idMgr;
    private EventService evSvc;
    private ChannelManager chMgr;
    private EventStreamer streamer;

    private Map<String, Boolean> tokens = new HashMap<>();

    public MonolithGridepo(GridepoConfig cfg) {
        jwtAlgo = Algorithm.HMAC256(cfg.getCrypto().getSeed().get("jwt"));
        jwtVerifier = JWT.require(jwtAlgo)
                .withIssuer(cfg.getDomain())
                .build();

        this.cfg = cfg;

        // FIXME use ServiceLoader
        if (StringUtils.equals("memory", cfg.getStorage().getType())) {
            store = new MemoryStore();
        } else {
            throw new IllegalArgumentException("Unknown storage: " + cfg.getStorage().getType());
        }

        DataServerManager dsmgr = new DataServerManager();
        KeyManager keyMgr = new KeyManager(new MemoryKeyStore());
        SignManager signMgr = new SignManager(keyMgr);
        evSvc = new EventService(cfg.getDomain(), signMgr);

        idMgr = new IdentityManager(store);
        chMgr = new ChannelManager(cfg, evSvc, store, dsmgr);
        streamer = new EventStreamer(store);
    }

    @Override
    public void start() {
        isStopping = false;

        if (StringUtils.isBlank(cfg.getDomain())) {
            throw new RuntimeException("Configuration: domain cannot be blank");
        }
    }

    @Override
    public void stop() {
        isStopping = true;
        synchronized (getSyncLock()) {
            getSyncLock().notifyAll();
        }
    }

    @Override
    public boolean isStopping() {
        return isStopping;
    }

    @Override
    public String getDomain() {
        return cfg.getDomain();
    }

    @Override
    public ChannelManager getChannelManager() {
        return chMgr;
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
    public UserSession login(String username, String password) {
        String canonicalUsername = idMgr.login(username, password);
        UserID uId = UserID.from(username, cfg.getDomain());
        User u = new User(uId, canonicalUsername);

        String token = JWT.create()
                .withIssuer(cfg.getDomain())
                .withExpiresAt(Date.from(Instant.ofEpochMilli(Long.MAX_VALUE)))
                .withClaim("UserID", uId.full())
                .withClaim("Username", username)
                .sign(jwtAlgo);

        tokens.put(token, true);
        return new UserSession(this, u, token);
    }

    @Override
    public void logout(UserSession session) {
        if (Objects.isNull(tokens.remove(session.getAccessToken()))) {
            throw new ObjectNotFoundException("Client access token", "<REDACTED>");
        }
    }

    @Override
    public UserSession withToken(String token) {
        if (!tokens.computeIfAbsent(token, t -> false)) {
            throw new InvalidTokenException("Unknown token");
        }

        DecodedJWT data = JWT.decode(token);
        UserID uId = UserID.parse(data.getClaim("UserID").asString());
        String username = JWT.decode(token).getClaim("Username").asString();
        return new UserSession(this, new User(uId, username), token);
    }

    @Override
    public boolean isLocal(UserID uId) {
        return getDomain().equals(new String(Base64.decode(uId.getId()), StandardCharsets.UTF_8).split("@", 2)[1]);
    }

    @Override
    public ServerSession forServer(String srvId) {
        return new ServerSession(this, srvId);
    }

    @Override
    public Object getSyncLock() {
        return syncLock;
    }

    @Override
    public Store getStore() {
        return store;
    }

}
