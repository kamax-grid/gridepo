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

package io.kamax.grid.gridepo.http.handler.matrix;

import io.kamax.grid.gridepo.core.SyncData;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncResponse {

    public static class RoomEvent {

        public static RoomEvent build(ChannelEvent ev) {
            return GsonUtil.get().fromJson(ProtocolEventMapper.convert(ev), RoomEvent.class);
        }

        private String eventId;
        private String type;
        private long originServerTs;
        private String roomId;
        private String sender;
        private String stateKey;
        private Object content;

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public long getOriginServerTs() {
            return originServerTs;
        }

        public void setOriginServerTs(long originServerTs) {
            this.originServerTs = originServerTs;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getStateKey() {
            return stateKey;
        }

        public void setStateKey(String stateKey) {
            this.stateKey = stateKey;
        }

        public Object getContent() {
            return content;
        }

        public void setContent(Object content) {
            this.content = content;
        }

    }

    public static class RoomState {

        private List<RoomEvent> events = new ArrayList<>();

        public List<RoomEvent> getEvents() {
            return events;
        }

        public void setEvents(List<RoomEvent> events) {
            this.events = events;
        }

    }

    public static class RoomTimeline {

        private List<RoomEvent> events = new ArrayList<>();

        public List<RoomEvent> getEvents() {
            return events;
        }

        public void setEvents(List<RoomEvent> events) {
            this.events = events;
        }

    }

    public static class Room {

        private RoomState state = new RoomState();
        private RoomTimeline timeline = new RoomTimeline();

        public RoomState getState() {
            return state;
        }

        public void setState(RoomState state) {
            this.state = state;
        }

        public RoomTimeline getTimeline() {
            return timeline;
        }

        public void setTimeline(RoomTimeline timeline) {
            this.timeline = timeline;
        }

    }

    public static class Rooms {

        private Map<String, Room> invite = new HashMap<>();
        private Map<String, Room> join = new HashMap<>();
        private Map<String, Room> leave = new HashMap<>();

        public Map<String, Room> getInvite() {
            return invite;
        }

        public void setInvite(Map<String, Room> invite) {
            this.invite = invite;
        }

        public Map<String, Room> getJoin() {
            return join;
        }

        public void setJoin(Map<String, Room> join) {
            this.join = join;
        }

        public Map<String, Room> getLeave() {
            return leave;
        }

        public void setLeave(Map<String, Room> leave) {
            this.leave = leave;
        }

    }

    private String nextBatch = "";
    private Rooms rooms = new Rooms();

    public SyncResponse(SyncData data) {
        setNextBatch(data.getPosition());
        data.getEvents().forEach(ev -> {
            RoomEvent rEv = RoomEvent.build(ev);
            rooms.join.computeIfAbsent(rEv.getRoomId(), r -> new Room()).getTimeline().getEvents().add(rEv);
        });
    }

    public String getNextBatch() {
        return nextBatch;
    }

    public void setNextBatch(String nextBatch) {
        this.nextBatch = nextBatch;
    }

}
