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

package io.kamax.grid.gridepo.core.signal;

import net.engio.mbassy.bus.SyncMessageBus;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.config.Feature;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SignalBus {

    private final Map<String, SyncMessageBus<Signal>> buses = new ConcurrentHashMap<>();

    private SyncMessageBus<Signal> forTopic(String topic) {
        return buses.computeIfAbsent(topic, k -> new SyncMessageBus<>(new BusConfiguration()
                .addFeature(Feature.SyncPubSub.Default())
                .addPublicationErrorHandler(new IPublicationErrorHandler.ConsoleLogger())));
    }

    public SyncMessageBus<Signal> forTopic(SignalTopic topic) {
        return forTopic(topic.name().toLowerCase());
    }

    public SyncMessageBus<Signal> getMain() {
        return forTopic(SignalTopic.Main);
    }

}
