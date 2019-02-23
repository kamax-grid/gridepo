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

package io.kamax.grid.gridepo.core.federation;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FederationPusher {

    private static final Logger log = LoggerFactory.getLogger(FederationPusher.class);

    private final Gridepo g;
    private final DataServerManager srvMgr;
    private final ForkJoinPool pool;

    public FederationPusher(Gridepo g, DataServerManager srvMgr) {
        this.g = g;
        this.srvMgr = srvMgr;

        this.pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 8);

        g.getBus().forTopic(SignalTopic.Channel).subscribe(this);
    }

    @Handler
    private void handle(ChannelMessageProcessed signal) {
        log.info("Got event {} to process", signal.getEvent().getSid());
        if (!g.isOrigin(signal.getEvent().getOrigin())) {
            log.info("Origin check: {} is not an local origin", signal.getEvent().getOrigin());
            return;
        }

        if (!signal.getAuth().isAuthorized()) {
            log.info("Auth check: not authorized");
            return;
        }

        ChannelEvent ev = signal.getEvent();
        pool.submit(new RecursiveAction() {
            @Override
            protected void compute() {
                ChannelState state = g.getChannelManager().get(ev.getChannelId()).getState(ev);
                List<String> servers = state.getServers().stream()
                        .filter(v -> !g.isOrigin(v))
                        .collect(Collectors.toList());

                log.info("Will push to {} server(s)", servers.size());

                invokeAll(srvMgr.get(servers).stream().map(srv -> new RecursiveAction() {
                    @Override
                    protected void compute() {
                        srv.push(g.getOrigin().full(), ev);
                        log.info("Event {}{} was pushed to {}", ev.getChannelId(), ev.getId(), srv.getId().full());
                    }
                }).collect(Collectors.toList()));

                log.info("Done pushing event {}", ev.getSid());
            }
        });
    }

    public void stop() {
        log.info("Stopping");
        pool.shutdown();
        pool.awaitQuiescence(1, TimeUnit.MINUTES);
        log.info("Stopped");
    }

}
