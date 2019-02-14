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
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Date;

public class MonolithGridepo implements Gridepo {

    private final Object syncLock = new Object();
    private boolean isStopping;

    private Algorithm jwtAlgo;
    private JWTVerifier jwtVerifier;

    private GridepoConfig cfg;
    private Store store;
    private IdentityManager idMgr;
    private ChannelManager chMgr;
    private EventStreamer streamer;

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
        EventService evSvc = new EventService(cfg.getDomain(), signMgr);

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
    public EventStreamer getStreamer() {
        return streamer;
    }

    @Override
    public UserSession login(String username, String password) {
        String canonicalUsername = idMgr.login(username, password);
        UserID uId = UserID.from(username, cfg.getDomain());
        User u = new User(canonicalUsername);

        String token = JWT.create()
                .withIssuer(cfg.getDomain())
                .withExpiresAt(Date.from(Instant.ofEpochMilli(Long.MAX_VALUE)))
                .withClaim("UserID", uId.full())
                .sign(jwtAlgo);

        return new UserSession(u, token);
    }

    @Override
    public UserSession withToken(String token) {
        String userId = JWT.decode(token).getClaim("UserID").asString();
        return new UserSession(this, new User(userId));
    }

    @Override
    public Object getSyncLock() {
        return syncLock;
    }

}
