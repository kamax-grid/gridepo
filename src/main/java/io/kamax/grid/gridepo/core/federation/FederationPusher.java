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
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.util.KxLog;
import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class FederationPusher {

    private static final Logger log = KxLog.make(FederationPusher.class);

    private final Gridepo g;
    private final DataServerManager srvMgr;
    private final ForkJoinPool pool;

    private boolean enabled;
    private boolean async;

    public FederationPusher(Gridepo g, DataServerManager srvMgr) {
        this.g = g;
        this.srvMgr = srvMgr;

        this.pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 8);
        setEnabled(true);
        setAsync(true);
    }

    @Handler
    private void handle(ChannelMessageProcessed signal) {
        if (!enabled) {
            return;
        }

        log.info("Got event {} to process", signal.getEvent().getLid());
        if (!g.isOrigin(signal.getEvent().getOrigin())) {
            log.info("Origin check: {} is not an local origin", signal.getEvent().getOrigin());
            return;
        }

        if (!signal.getAuth().isAuthorized()) {
            log.info("Auth check: not authorized");
            return;
        }

        ChannelEvent ev = signal.getEvent();
        ForkJoinTask<Void> task = pool.submit(new RecursiveAction() {
            @Override
            protected void compute() {
                Set<ServerID> servers = g.getChannelManager().get(ev.getChannelId()).getView().getOtherServers();
                log.info("Will push to {} server(s)", servers.size());

                invokeAll(srvMgr.get(servers).stream().map(srv -> new RecursiveAction() {
                    @Override
                    protected void compute() {
                        srv.push(g.getOrigin().full(), ev);
                        log.info("Event {}{} was pushed to {}", ev.getChannelId(), ev.getId(), srv.getId().full());
                    }
                }).collect(Collectors.toList()));

                log.info("Done pushing event {}", ev.getLid());
            }
        });

        if (!async) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        log.info("Stopping");
        pool.shutdown();
        pool.awaitQuiescence(1, TimeUnit.MINUTES);
        log.info("Stopped");
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (enabled) {
            g.getBus().forTopic(SignalTopic.Channel).subscribe(this);
        } else {
            g.getBus().forTopic(SignalTopic.Channel).unsubscribe(this);
        }
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

}
