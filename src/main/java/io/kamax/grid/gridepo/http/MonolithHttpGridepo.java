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

package io.kamax.grid.gridepo.http;

import com.google.gson.JsonArray;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.MonolithGridepo;
import io.kamax.grid.gridepo.http.handler.matrix.*;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.TlsUtils;
import io.undertow.Handlers;
import io.undertow.Undertow;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.ssl.SSLContextBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.FileReader;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class MonolithHttpGridepo {

    private static final Logger log = LoggerFactory.getLogger(MonolithHttpGridepo.class);

    private GridepoConfig cfg;
    private MonolithGridepo g;
    private Undertow u;

    public MonolithHttpGridepo(String domain) {
        GridepoConfig cfg = new GridepoConfig();
        cfg.setDomain(domain);
        init(cfg);
    }

    public MonolithHttpGridepo(GridepoConfig cfg) {
        init(cfg);
    }

    private void init(GridepoConfig cfg) {
        this.cfg = cfg;
    }

    private SSLContext buildTls(String keyFilePath, String certFilePath) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
            PemReader key = new PemReader(new FileReader(keyFilePath));

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key.readPemObject().getContent());
            PrivateKey privateKey = factory.generatePrivate(keySpec);

            X509Certificate certC = TlsUtils.load(certFilePath);
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, "".toCharArray());
            store.setKeyEntry("federation", privateKey, "".toCharArray(), new Certificate[]{certC});
            return SSLContextBuilder.create().loadKeyMaterial(store, "".toCharArray()).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void buildGridClient(Undertow.Builder b, GridepoConfig.Listener cfg) {

    }

    private void buildGridServer(Undertow.Builder b, GridepoConfig.Listener cfg) {
        //SSLContext sslC = buildTls(cfg.getFederation().getKey(), cfg.getFederation().getCert());
        //b.addHttpsListener(cfg.getFederation().getPort(), cfg.getFederation().getIp(), sslC).setHandler(Handlers.routing());
    }

    private void buildGrid(Undertow.Builder b, GridepoConfig.Listener cfg) {
        if (StringUtils.equals("client", cfg.getType())) {
            buildGridClient(b, cfg);
        } else if (StringUtils.equals("server", cfg.getType())) {
            buildGridServer(b, cfg);
        } else {
            throw new RuntimeException(cfg.getType() + " is not a supported Grid listener type");
        }
    }

    private void buildMatrixClient(Undertow.Builder b, GridepoConfig.Listener cfg) {
        b.addHttpListener(cfg.getPort(), cfg.getAddress()).setHandler(Handlers.routing()
                // CORS support
                .add("OPTIONS", "/**", new OptionsHandler())

                // Fundamental endpoints
                .get(ClientAPI.Base + "/versions", new VersionsHandler())
                .get(ClientAPIr0.Base + "/login", new LoginGetHandler())
                .post(ClientAPIr0.Base + "/login", new LoginHandler(g))
                .get(ClientAPIr0.Base + "/sync", new SyncHandler(g))
                .post(ClientAPIr0.Base + "/logout", new LogoutHandler(g))

                // Account endpoints
                .get(ClientAPIr0.Base + "/account/3pid", new JsonObjectHandler(
                        g,
                        true,
                        GsonUtil.makeObj("threepids", new JsonArray()))
                )

                // User-related endpoints
                .get(ClientAPIr0.Base + "/profile/**", new EmptyJsonObjectHandler(g, false))
                .post(ClientAPIr0.Base + "/user_directory/search", new UserDirectorySearchHandler(g))

                // Room endpoints
                .post(ClientAPIr0.Base + "/createRoom", new CreateRoomHandler(g))
                .put(ClientAPIr0.Room + "/send/{type}/{txnId}", new SendChannelEventHandler(g))
                .post(ChannelInviteHandler.Path, new ChannelInviteHandler(g))
                .post(ClientAPIr0.Base + "/join/{roomId}", new ChannelJoinHandler(g))

                // Not supported over Matrix
                .post(ClientAPIr0.Room + "/read_markers", new EmptyJsonObjectHandler(g, true))
                .post(ClientAPIr0.Room + "/typing/{userId}", new EmptyJsonObjectHandler(g, true))
                .get(ClientAPIr0.Base + "/user/{userId}/filter/{filterId}", new FilterGetHandler(g))
                .post(ClientAPIr0.Base + "/user/{userId}/filter", new FiltersPostHandler(g))

                // So various Matrix clients (e.g. Riot) stops spamming us with requests
                .get(ClientAPIr0.Base + "/pushrules/", new PushRulesHandler())
                .put(ClientAPIr0.Base + "/presence/**", new EmptyJsonObjectHandler(g, true))
                .get(ClientAPIr0.Base + "/voip/turnServer", new EmptyJsonObjectHandler(g, true))
                .get(ClientAPIr0.Base + "/joined_groups", new EmptyJsonObjectHandler(g, true))

                .setFallbackHandler(new NotFoundHandler())
        );
        log.info("Added Matrix client listener on {}:{}", cfg.getAddress(), cfg.getPort());
    }

    private void buildMatrixServer(Undertow.Builder b, GridepoConfig.Listener cfg) {

    }

    private void buildMatrix(Undertow.Builder b, GridepoConfig.Listener cfg) {
        if (StringUtils.equals("client", cfg.getType())) {
            buildMatrixClient(b, cfg);
        } else if (StringUtils.equals("server", cfg.getType())) {
            buildMatrixServer(b, cfg);
        } else {
            throw new RuntimeException(cfg.getType() + " is not a supported Matrix listener type");
        }
    }

    private void build() {
        g = new MonolithGridepo(cfg);

        Undertow.Builder b = Undertow.builder();
        for (GridepoConfig.Listener cfg : cfg.getListeners()) {
            if (StringUtils.equals("grid", cfg.getProtocol())) {
                buildGrid(b, cfg);
            } else if (StringUtils.equals("matrix", cfg.getProtocol())) {
                buildMatrix(b, cfg);
            } else {
                throw new RuntimeException(cfg.getProtocol() + " is not a supported listener protocol");
            }
        }
        u = b.build();
    }

    public void start() {
        build();

        g.start();
        u.start();
    }

    public void stop() {
        try {
            ForkJoinPool.commonPool().submit(new RecursiveAction() {
                @Override
                protected void compute() {
                    invokeAll(new RecursiveAction() {
                        @Override
                        protected void compute() {
                            u.stop();
                        }
                    }, new RecursiveAction() {
                        @Override
                        protected void compute() {
                            g.stop();
                        }
                    });
                }
            }).get();
        } catch (InterruptedException e) {
            log.info("Shutdown is dirty: interrupted while waiting for components to stop");
        } catch (ExecutionException e) {
            log.info("Shutdown is dirty: failure while waiting for components to stop", e);
        }
    }

}
