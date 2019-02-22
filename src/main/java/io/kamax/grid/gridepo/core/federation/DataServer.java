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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class DataServer {

    private transient final Logger log = LoggerFactory.getLogger(DataServer.class);

    private String domain;
    private DataServerHttpClient client;
    private volatile Instant lastCall;
    private volatile Instant lastActivity;
    private AtomicLong waitTime = new AtomicLong();

    public DataServer(String domain) {
        this.domain = domain;
        this.client = new DataServerHttpClient(domain);
        setAvailable();
    }

    private <T> T withHealthCheck(Supplier<T> r) {
        return withHealthCheck(false, r);
    }

    private <T> T withHealthCheck(boolean force, Supplier<T> r) {
        Instant nextRetry = lastCall.plusMillis(waitTime.get());
        if (Instant.now().isBefore(nextRetry)) {
            throw new RuntimeException("Host is not available at this time. Next window is in " + Duration.between(Instant.now(), nextRetry).getSeconds() + " seconds");
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

    public String getDomain() {
        return domain;
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

    public Optional<JsonObject> getEvent(String chId, String evId) {
        return Optional.empty();
    }

    public JsonObject approveEvent(JsonObject ev) {
        return withHealthCheck(true, () -> client.approveEvent(domain, ev));
    }

    /*
    public JsonObject send(String method, String path, Map<String, String> parameters, JsonElement payload) {
        return withHealthCheck(() -> client.send(domain, method, path, parameters, payload));
    }
    */

}
