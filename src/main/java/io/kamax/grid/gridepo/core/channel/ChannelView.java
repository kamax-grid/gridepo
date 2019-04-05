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

package io.kamax.grid.gridepo.core.channel;

import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEventType;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.Set;
import java.util.stream.Collectors;

public class ChannelView {

    private ServerID origin;
    private EventID head;
    private ChannelState state;

    public ChannelView(ServerID origin) {
        this(origin, null, ChannelState.empty());
    }

    public ChannelView(ServerID origin, EventID head, ChannelState state) {
        this.origin = origin;
        this.head = head;
        this.state = state;
    }

    public EventID getHead() {
        return head;
    }

    public ChannelState getState() {
        return state;
    }

    public Set<ServerID> getAllServers() {
        return getServers(true);
    }

    public Set<ServerID> getOtherServers() {
        return getServers(false);
    }

    public Set<ServerID> getServers(boolean includeSelf) {
        return getState().getEvents().stream()
                .filter(ev -> ChannelEventType.Member.match(ev.getBare().getType()))
                .filter(bEv -> {
                    BareMemberEvent ev = GsonUtil.fromJson(bEv.getData(), BareMemberEvent.class);
                    return ChannelMembership.Join.match(ev.getContent().getAction());
                })
                .map(ev -> ServerID.parse(ev.getOrigin()))
                .filter(id -> !origin.equals(id) || includeSelf)
                .collect(Collectors.toSet());
    }

    public boolean isJoined(ServerID id) {
        // TODO fix, not restricted to joined servers
        return getAllServers().contains(id);
    }

}
