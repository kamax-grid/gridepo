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

package io.kamax.grid.gridepo.core.event;

import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.store.DataStore;

import java.util.List;

public class EventStreamer {

    private final DataStore store;

    public EventStreamer(DataStore store) {
        this.store = store;
    }

    public List<ChannelEvent> next(long sid) {
        return store.getNext(sid, 20);
    }

    public List<ChannelEvent> next(ChannelEvent ev) {
        return next(ev.getSid());
    }

    public long getPosition() {
        return store.getStreamPosition();
    }

}
