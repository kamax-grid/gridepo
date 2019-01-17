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

package io.kamax.grid.gridepo.core.channel.event;

import com.google.gson.annotations.SerializedName;
import io.kamax.grid.gridepo.core.event.EventKey;

import java.util.HashMap;
import java.util.Map;

public class BarePowerEvent extends BareEvent {

    public static class Content {

        public static class Default {

            private Long event;
            private Long state;
            private Long user;

            public Long getEvent() {
                return event;
            }

            public void setEvent(Long event) {
                this.event = event;
            }

            public Long getState() {
                return state;
            }

            public void setState(Long state) {
                this.state = state;
            }

            public Long getUser() {
                return user;
            }

            public void setUser(Long user) {
                this.user = user;
            }

        }

        public static class Membership {

            private Long ban;
            private Long kick; // FIXME would using 'leave' and a map instead of hardcoded value work?
            private Long invite;

            public Long getBan() {
                return ban;
            }

            public void setBan(Long ban) {
                this.ban = ban;
            }

            public Long getKick() {
                return kick;
            }

            public void setKick(Long kick) {
                this.kick = kick;
            }

            public Long getInvite() {
                return invite;
            }

            public void setInvite(Long invite) {
                this.invite = invite;
            }

        }

        @SerializedName("default")
        private Default def = new Default();
        @SerializedName("member")
        private Membership membership = new Membership();
        @SerializedName("events")
        private Map<String, Long> events = new HashMap<>();
        @SerializedName("users")
        private Map<String, Long> users = new HashMap<>();

        public Default getDef() {
            return def;
        }

        public void setDef(Default def) {
            this.def = def;
        }

        public Membership getMembership() {
            return membership;
        }

        public void setMembership(Membership membership) {
            this.membership = membership;
        }

        public Map<String, Long> getEvents() {
            return events;
        }

        public void setEvents(Map<String, Long> events) {
            this.events = events;
        }

        public Map<String, Long> getUsers() {
            return users;
        }

        public void setUsers(Map<String, Long> users) {
            this.users = users;
        }

    }

    @SerializedName(EventKey.Content)
    private Content content = new Content();

    public BarePowerEvent() {
        setType(ChannelEventType.Power);
        setScope("");
    }

    @Override
    public Content getContent() {
        return content;
    }

}
