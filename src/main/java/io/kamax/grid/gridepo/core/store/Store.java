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
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Store {

    Optional<ChannelDao> findChannel(long cSid);

    Optional<ChannelDao> findChannel(ChannelID cId);

    default ChannelDao getChannel(long cSid) {
        return findChannel(cSid).orElseThrow(() -> new ObjectNotFoundException("Channel", Long.toString(cSid)));
    }

    long addToStream(long eLid);

    ChannelDao saveChannel(ChannelDao ch);

    ChannelEvent saveEvent(ChannelEvent ev);

    ChannelEvent getEvent(ChannelID cId, EventID eId) throws ObjectNotFoundException;

    ChannelEvent getEvent(long eSid);

    EventID getEventId(long eSid);

    Optional<Long> findEventLid(ChannelID cId, EventID eId);

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

    long getUserCount();

    long storeUser(String username, String password);

    Optional<UserDao> findUser(long lid);

    Optional<UserDao> findUser(String username);

    boolean hasUserAccessToken(String token);

    void insertUserAccessToken(long uLid, String token);

    void deleteUserAccessToken(String token);

    Optional<ChannelID> lookupChannelAlias(String chAlias);

    Set<String> findChannelAlias(ServerID origin, ChannelID cId);

    void setAliases(ServerID origin, ChannelID cId, Set<String> chAliases);

    void unmap(String cAlias);

}
