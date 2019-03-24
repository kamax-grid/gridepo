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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.ChannelAlias;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.channel.ChannelLookup;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.structure.ApprovalExchange;
import io.kamax.grid.gridepo.core.channel.structure.InviteApprovalRequest;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class DataServer {

    private static final Logger log = KxLog.make(DataServer.class);

    private final ServerID id;
    private final String hostname;
    private DataServerHttpClient client;
    private volatile Instant lastCall;
    private volatile Instant lastActivity;
    private AtomicLong waitTime = new AtomicLong();

    public DataServer(ServerID id) {
        this.id = id;
        this.hostname = id.tryDecode().orElseThrow(() -> new IllegalArgumentException("Unable to resolve " + id.full() + " to a hostname"));
        this.client = new DataServerHttpClient();
        setAvailable();
    }

    private <T> T withHealthCheck(Supplier<T> r) {
        return withHealthCheck(false, r);
    }

    private <T> T withHealthCheck(boolean force, Supplier<T> r) {
        if (!force) {
            Instant nextRetry = lastCall.plusMillis(waitTime.get());
            if (Instant.now().isBefore(nextRetry)) {
                throw new RuntimeException("Host is not available at this time. Next window is in " + Duration.between(Instant.now(), nextRetry).getSeconds() + " seconds");
            }
        }

        try {
            T v = r.get();
            setAvailable();
            return v;
        } catch (FederationException e) {
            throw e;
        } catch (Throwable t) {
            if (waitTime.get() == 0) {
                waitTime.set(1000);
            } else {
                synchronized (this) {
                    waitTime.updateAndGet(l -> l * 2);
                }
            }
            throw t;
        }
    }

    public ServerID getId() {
        return id;
    }

    public long getLastCall() {
        return lastCall.toEpochMilli();
    }

    public long getLastActivity() {
        return lastActivity.toEpochMilli();
    }

    public boolean isAvailable() {
        return waitTime.get() == 0;
    }

    public void setAvailable() {
        lastCall = Instant.now();
        waitTime.set(0);
    }

    public void updateActivity() {
        lastActivity = Instant.now();
    }

    public void setActive() {
        updateActivity();
        setAvailable();
    }

    public Optional<JsonObject> getEvent(ChannelID chId, EventID evId) {
        return Optional.empty();
    }

    public JsonObject push(String as, ChannelEvent ev) {
        log.info("Pushing event {} to {} ({}) as {}", ev.getSid(), id, hostname, as);
        return withHealthCheck(() -> client.push(as, hostname, Collections.singletonList(ev)));
    }

    public JsonObject approveInvite(String as, InviteApprovalRequest data) {
        return withHealthCheck(true, () -> client.approveInvite(as, hostname, data));
    }

    public ApprovalExchange approveJoin(String as, BareMemberEvent ev) {
        return withHealthCheck(true, () -> {
            JsonObject json = client.approveJoin(as, hostname, ev);
            return GsonUtil.fromJson(json, ApprovalExchange.class);
        });
    }

    public Optional<ChannelLookup> lookup(String as, ChannelAlias alias) {
        return withHealthCheck(true, () -> client.lookup(as, hostname, alias));
    }

}
