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

public class BareMessageEvent extends BareEvent {

    public static class Content {

        private Map<String, String> body = new HashMap<>();

        public Map<String, String> getBody() {
            return body;
        }

        public void setBody(Map<String, String> body) {
            this.body = body;
        }

    }

    @SerializedName(EventKey.Content)
    private Content content = new Content();

    public BareMessageEvent() {
        setType(ChannelEventType.Message);
        setScope("");
    }

    @Override
    public Content getContent() {
        return content;
    }

    public void addBody(String mimeType, String body) {
        content.getBody().put(mimeType, body);
    }

}
