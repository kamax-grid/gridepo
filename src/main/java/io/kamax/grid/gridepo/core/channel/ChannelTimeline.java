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
import io.kamax.grid.gridepo.core.store.Store;

public class ChannelTimeline {

    private long id;
    private Store store;

    public ChannelTimeline(long id, Store store) {
        this.id = id;
        this.store = store;
    }

    public TimelineChunk getNext(EventID evId, long amount) {
        long eTid = store.getEventTid(id, evId);
        TimelineChunk chunk = new TimelineChunk();
        chunk.setStart(evId);
        chunk.setEnd(evId);
        chunk.setEvents(store.getTimelineNext(id, eTid, amount));
        if (!chunk.getEvents().isEmpty()) {
            chunk.setEnd(chunk.getEvents().get(chunk.getEvents().size() - 1).getId());
        }
        return chunk;
    }

    public TimelineChunk getPrevious(EventID evId, long amount) {
        long eTid = store.getEventTid(id, evId);
        TimelineChunk chunk = new TimelineChunk();
        chunk.setStart(evId);
        chunk.setEnd(evId);
        chunk.setEvents(store.getTimelinePrevious(id, eTid, amount));
        if (!chunk.getEvents().isEmpty()) {
            chunk.setEnd(chunk.getEvents().get(chunk.getEvents().size() - 1).getId());
        }
        return chunk;
    }

}
