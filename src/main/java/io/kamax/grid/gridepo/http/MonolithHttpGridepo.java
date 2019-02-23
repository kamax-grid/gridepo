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
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.MonolithGridepo;
import io.kamax.grid.gridepo.http.handler.grid.server.DoApproveInvite;
import io.kamax.grid.gridepo.http.handler.grid.server.DoPushHandler;
import io.kamax.grid.gridepo.http.handler.matrix.*;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class MonolithHttpGridepo {

    private static final Logger log = LoggerFactory.getLogger(MonolithHttpGridepo.class);

    private GridepoConfig cfg;
    private MonolithGridepo g;
    private Undertow u;

    public MonolithHttpGridepo(GridepoConfig cfg) {
        init(cfg);
    }

    private void init(GridepoConfig cfg) {
        this.cfg = cfg;
    }

    private void buildGridClient(RoutingHandler handler) {
        log.warn("Tried to add Grid client endpoints but not implemented yet");

        handler
                .add("OPTIONS", "/_grid/client", new OptionsHandler())
        ;
    }

    private void buildGridServer(RoutingHandler handler) {
        handler
                .post("/_grid/server/v0/do/approve/invite", new DoApproveInvite(g))
                .post("/_grid/server/v0/do/push", new DoPushHandler(g))
        ;
    }

    private void buildGrid(RoutingHandler handler, GridepoConfig.ListenerNetwork network) {
        if (StringUtils.equals("client", network.getType())) {
            buildGridClient(handler);
        } else if (StringUtils.equals("server", network.getType())) {
            buildGridServer(handler);
        } else {
            throw new RuntimeException(network.getType() + " is not a supported Grid listener type");
        }
    }

    private void buildMatrixClient(RoutingHandler handler) {
        handler
                // CORS support
                .add("OPTIONS", ClientAPI.Base, new OptionsHandler())

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

                // Room management endpoints
                .post(ClientAPIr0.Base + "/createRoom", new CreateRoomHandler(g))
                .post(ClientAPIr0.Room + "/invite", new ChannelInviteHandler(g))
                .post(ClientAPIr0.Base + "/join/{roomId}", new ChannelJoinHandler(g))
                .post(ClientAPIr0.Room + "/leave", new ChannelLeaveHandler(g))
                .post(ClientAPIr0.Room + "/forget", new EmptyJsonObjectHandler(g, true))

                // Room event endpoints
                .put(ClientAPIr0.Room + "/send/{type}/{txnId}", new SendChannelEventHandler(g))

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

                .setFallbackHandler(new NotFoundHandler());

        log.info("Added Matrix client listener");
    }

    private void buildMatrix(RoutingHandler handler, GridepoConfig.ListenerNetwork network) {
        if (StringUtils.equals("client", network.getType())) {
            buildMatrixClient(handler);
        } else {
            throw new RuntimeException(network.getType() + " is not a supported Matrix listener type");
        }
    }

    private void build() {
        if (cfg.getListeners().isEmpty()) {
            GridepoConfig.Listener l = new GridepoConfig.Listener();
            l.setAddress("0.0.0.0");
            l.setPort(9009);
            l.addNetwork(GridepoConfig.ListenerNetwork.build("grid", "client"));
            l.addNetwork(GridepoConfig.ListenerNetwork.build("grid", "server"));
            l.addNetwork(GridepoConfig.ListenerNetwork.build("matrix", "client"));
            cfg.getListeners().add(l);
        }

        g = new MonolithGridepo(cfg);

        Undertow.Builder b = Undertow.builder();
        for (GridepoConfig.Listener cfg : cfg.getListeners()) {
            log.info("Creating HTTP listener on {}:{}", cfg.getAddress(), cfg.getPort());
            RoutingHandler handler = Handlers.routing();

            for (GridepoConfig.ListenerNetwork network : cfg.getNetwork()) {
                if (StringUtils.equals("grid", network.getProtocol())) {
                    buildGrid(handler, network);
                } else if (StringUtils.equals("matrix", network.getProtocol())) {
                    buildMatrix(handler, network);
                } else {
                    throw new RuntimeException(network.getProtocol() + " is not a supported listener protocol");
                }
            }

            b.addHttpListener(cfg.getPort(), cfg.getAddress()).setHandler(handler);
        }

        u = b.build();
    }

    public Gridepo start() {
        build();

        g.start();
        u.start();

        return g;
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
            log.info("Shutdown is dirty: unknown failure while waiting for components to stop", e.getCause());
        }
    }

}
