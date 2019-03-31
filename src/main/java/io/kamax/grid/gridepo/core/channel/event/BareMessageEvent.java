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

import io.kamax.grid.gridepo.core.UserID;

import java.util.HashMap;
import java.util.Map;

public class BareMessageEvent extends BareEvent<BareMessageEvent.Content> {

    public static BareMessageEvent build(UserID sender, String text) {
        BareMessageEvent ev = new BareMessageEvent();
        ev.setSender(sender);
        ev.addBody("text/plain", text);
        return ev;
    }

    public static class Content {

        private String type;
        private Map<String, String> body = new HashMap<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, String> getBody() {
            return body;
        }

        public void setBody(Map<String, String> body) {
            this.body = body;
        }

    }

    public BareMessageEvent() {
        setType(ChannelEventType.Message);
        setContent(new Content());
    }

    public void addBody(String mimeType, String body) {
        getContent().getBody().put(mimeType, body);
    }

}
