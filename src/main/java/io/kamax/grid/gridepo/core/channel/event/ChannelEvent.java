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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.store.postgres.ChannelEventMeta;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ChannelEvent {

    public static ChannelEvent forNotFound(long cSid, EventID evId) {
        ChannelEvent ev = new ChannelEvent(cSid);
        ev.id = evId;
        ev.getMeta().setPresent(false);
        return ev;
    }

    public static ChannelEvent forNotFound(long cSid, String evId) {
        return forNotFound(cSid, EventID.from(evId));
    }

    public static ChannelEvent from(long cSid, JsonObject raw) {
        ChannelEvent ev = new ChannelEvent(cSid);
        ev.setData(raw);
        return ev;
    }

    private long cSid;
    private Long sid;
    private EventID id;
    private JsonObject data;
    private ChannelEventMeta meta;

    private transient BareGenericEvent bare;
    private transient List<EventID> prevEvents;

    public ChannelEvent() {
        meta = new ChannelEventMeta();
    }

    public ChannelEvent(long cSid) {
        this();

        this.cSid = cSid;
    }

    public ChannelEvent(long cSid, long sid) {
        this(cSid);

        this.sid = sid;
    }

    public ChannelEvent(long cSid, long sid, ChannelEventMeta meta) {
        this(cSid, sid);
        this.meta = meta;
    }

    public long getChannelSid() {
        return cSid;
    }

    public boolean hasSid() {
        return Objects.nonNull(sid);
    }

    public long getSid() {
        if (!hasSid()) {
            throw new IllegalStateException();
        }

        return sid;
    }

    public void setSid(long sid) {
        if (hasSid()) {
            throw new IllegalStateException();
        }

        this.sid = sid;
    }

    public EventID getId() {
        if (Objects.isNull(id)) {
            id = EventID.from(getBare().getId());
        }
        return id;
    }

    public String getOrigin() {
        return getBare().getOrigin();
    }

    public String getChannelId() {
        return getBare().getChannelId();
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data;
        bare = null;
    }

    public List<EventID> getPreviousEvents() {
        if (Objects.isNull(prevEvents)) {
            prevEvents = getBare().getPreviousEvents().stream().map(EventID::from).collect(Collectors.toList());
        }

        return prevEvents;
    }

    public BareGenericEvent getBare() {
        if (Objects.isNull(bare)) {
            bare = GsonUtil.fromJson(getData(), BareGenericEvent.class);
        }

        return bare;
    }

    public ChannelEventMeta getMeta() {
        return meta;
    }

}
