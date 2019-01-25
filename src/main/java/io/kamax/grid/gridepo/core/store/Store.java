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

package io.kamax.grid.gridepo.core.store;

import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;

import java.util.List;
import java.util.Optional;

public interface Store {

    default boolean hasEvent(String channelId, String eventId) {
        return getEvent(channelId, eventId).isPresent();
    }

    ChannelDao saveChannel(ChannelDao ch);

    ChannelEvent saveEvent(ChannelEvent ev);

    ChannelEvent getEvent(String channelId, String eventId) throws IllegalStateException;

    Optional<ChannelEvent> findEvent(String channelId, String eventId);

    void setExtremities(String chId, List<String> extremities);

    List<String> getExtremities(String channelId);

    long insertIfNew(String chId, ChannelState state);

    ChannelState getState(long stateSid);

    void map(long evSid, long stateSid);

}
