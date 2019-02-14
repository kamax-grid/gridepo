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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.SyncData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncResponse {

    public static class Room {

        private List<JsonObject> state = new ArrayList<>();
        private List<JsonObject> timeline = new ArrayList<>();

        public List<JsonObject> getState() {
            return state;
        }

        public void setState(List<JsonObject> state) {
            this.state = state;
        }

        public List<JsonObject> getTimeline() {
            return timeline;
        }

        public void setTimeline(List<JsonObject> timeline) {
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

        });
    }

    public String getNextBatch() {
        return nextBatch;
    }

    public void setNextBatch(String nextBatch) {
        this.nextBatch = nextBatch;
    }

}
