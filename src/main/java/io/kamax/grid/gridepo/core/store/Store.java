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

import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;

import java.util.List;
import java.util.Optional;

public interface Store {

    Optional<ChannelDao> findChannel(long cSid);

    default ChannelDao getChannel(long cSid) {
        return findChannel(cSid).orElseThrow(() -> new ObjectNotFoundException("Channel", Long.toString(cSid)));
    }

    ChannelDao saveChannel(ChannelDao ch);

    ChannelEvent saveEvent(ChannelEvent ev);

    ChannelEvent getEvent(ChannelID cId, EventID eId) throws ObjectNotFoundException;

    ChannelEvent getEvent(long eSid);

    EventID getEventId(long eSid);

    long getEventSid(ChannelID cId, EventID eId) throws ObjectNotFoundException;

    // Get the N next events. next = Higher SID
    List<ChannelEvent> getNext(long lastSid, long amount);

    Optional<ChannelEvent> findEvent(ChannelID cId, EventID eId);

    Optional<ChannelEvent> findEvent(long eSid);

    void updateExtremities(long cSid, List<Long> toRemove, List<Long> toAdd);

    List<Long> getExtremities(long cSid);

    long insertIfNew(long cSid, ChannelState state);

    ChannelState getState(long stateSid);

    void map(long evSid, long stateSid);

    ChannelState getStateForEvent(long evSid);

    boolean hasUser(String username);

    long storeUser(String username, String password);

    Optional<String> findPassword(String username);

    Optional<ChannelID> findChannelIdForAddress(String chAd);

    List<String> findChannelAddressForId(ChannelID cId);

    void map(ChannelID cId, String chAd);

    void unmap(String chAd);

}
