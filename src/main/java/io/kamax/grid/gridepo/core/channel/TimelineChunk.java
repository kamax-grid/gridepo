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
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;

import java.util.ArrayList;
import java.util.List;

public class TimelineChunk {

    private EventID start;
    private List<ChannelEvent> events = new ArrayList<>();
    private EventID end;

    public EventID getStart() {
        return start;
    }

    public void setStart(EventID start) {
        this.start = start;
    }

    public List<ChannelEvent> getEvents() {
        return events;
    }

    public void setEvents(List<ChannelEvent> events) {
        this.events = events;
    }

    public EventID getEnd() {
        return end;
    }

    public void setEnd(EventID end) {
        this.end = end;
    }

}
