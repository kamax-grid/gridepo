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

package io.kamax.grid.gridepo.network.grid.http.handler.matrix;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.SyncData;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.network.grid.ProtocolEventMapper;
import io.kamax.grid.gridepo.network.matrix.http.json.RoomEvent;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.util.*;

public class SyncResponse {

    private static final Logger log = KxLog.make(SyncResponse.class);

        public static RoomEvent build(ChannelEvent ev) {
            RoomEvent rEv = ProtocolEventMapper.forEventConvertToMatrix(ev);
            rEv.setChannelId(ev.getChannelId());
            if (log.isDebugEnabled()) {
                rEv.setGrid(ev.getData());
            }
            return rEv;
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
        private String prevBatch;
        private boolean limited = false;

        public List<RoomEvent> getEvents() {
            return events;
        }

        public void setEvents(List<RoomEvent> events) {
            this.events = events;
        }

        public String getPrevBatch() {
            return prevBatch;
        }

        public void setPrevBatch(String prevBatch) {
            this.prevBatch = prevBatch;
        }

        public boolean isLimited() {
            return limited;
        }

        public void setLimited(boolean limited) {
            this.limited = limited;
        }

    }

    public static class Room {

        private RoomState state = new RoomState();
        private RoomTimeline timeline = new RoomTimeline();
        private RoomState inviteState = new RoomState();

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

    public static SyncResponse build(Gridepo g, String uId, SyncData data) {
        Map<String, Room> roomCache = new HashMap<>();
        SyncResponse r = new SyncResponse();
        r.setNextBatch(data.getPosition());
        for (ChannelEvent ev : data.getEvents()) {
            try {
                RoomEvent rEv = build(ev);
                Room room = roomCache.computeIfAbsent(rEv.getRoomId(), id -> new Room());
                room.getTimeline().getEvents().add(rEv);
                if (data.isInitial()) {
                    // This is the first event, so we set the previous batch info
                    room.getTimeline().setPrevBatch(ev.getId().full());
                    ChannelState state = g.getChannelManager().get(ev.getChannelId()).getState(ev);
                    state.getEvents().stream()
                            .sorted(Comparator.comparingLong(o -> o.getBare().getDepth())) // FIXME use Timeline ordering
                            .forEach(stateEv -> room.getState().getEvents().add(build(stateEv)));
                }
                r.rooms.join.put(rEv.getRoomId(), room);

                if ("m.room.member".equals(rEv.getType()) && uId.equals(rEv.getStateKey())) {
                    JsonObject c = GsonUtil.parseObj(GsonUtil.toJson(rEv.getContent()));
                    GsonUtil.findString(c, "membership").ifPresent(m -> {
                        if ("invite".equals(m)) {
                            r.rooms.join.remove(rEv.getRoomId());
                            r.rooms.invite.put(rEv.getRoomId(), room);
                            r.rooms.leave.remove(rEv.getRoomId());

                            room.inviteState.events.addAll(room.timeline.events);
                            room.timeline.events.clear();
                            room.state.events.clear();
                        } else if ("leave".equals(m) || "ban".equals(m)) {
                            r.rooms.invite.remove(rEv.getRoomId());
                            r.rooms.join.remove(rEv.getRoomId());
                            r.rooms.leave.put(rEv.getRoomId(), room);
                        } else if (ChannelMembership.Join.match(m)) {
                            r.rooms.invite.remove(rEv.getRoomId());
                            r.rooms.join.put(rEv.getRoomId(), room);
                            r.rooms.leave.remove(rEv.getRoomId());

                            room.inviteState.events.clear();

                            room.state.events.clear();
                            g.getChannelManager().get(rEv.getChannelId()).getState(ev).getEvents().forEach(sEv -> {
                                if (sEv.getLid() != ev.getLid()) {
                                    room.state.events.add(build(sEv));
                                }
                            });
                        } else {
                            // unknown, not supported
                        }
                    });
                }
            } catch (RuntimeException e) {
                log.warn("Unable to map Grid event {} to Matrix event, ignoring", ev.getId(), e);
            }
        }

        return r;
    }

    private String nextBatch = "";
    private Rooms rooms = new Rooms();

    public String getNextBatch() {
        return nextBatch;
    }

    public void setNextBatch(String nextBatch) {
        this.nextBatch = nextBatch;
    }

}
