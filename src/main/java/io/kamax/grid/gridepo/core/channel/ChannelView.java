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
import io.kamax.grid.gridepo.core.channel.state.ChannelState;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ChannelView {

    private EventID head;
    private ChannelState state;

    private transient Set<ServerID> srvJoined;

    public ChannelView() {
        this(null, ChannelState.empty());
    }

    public ChannelView(EventID head, ChannelState state) {
        this.head = head;
        this.state = state;
    }

    public EventID getHead() {
        return head;
    }

    public ChannelState getState() {
        return state;
    }

    public Set<ServerID> getJoinedServers() {
        if (Objects.isNull(srvJoined)) {
            srvJoined = getState().getEvents().stream()
                    .map(ev -> ServerID.parse(ev.getOrigin()))
                    .collect(Collectors.toSet());
        }

        return srvJoined;
    }

    public boolean isJoined(ServerID id) {
        return getJoinedServers().contains(id);
    }

}
