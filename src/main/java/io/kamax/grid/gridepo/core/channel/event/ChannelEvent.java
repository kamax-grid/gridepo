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
import io.kamax.grid.gridepo.util.GsonUtil;

import java.time.Instant;
import java.util.Objects;

public class ChannelEvent {

    public static ChannelEvent forNotFound(String evId) {
        ChannelEvent ev = new ChannelEvent();
        ev.setId(evId);
        ev.setPresent(false);
        return ev;
    }

    public static ChannelEvent from(JsonObject raw) {
        ChannelEvent ev = new ChannelEvent();
        ev.setData(raw);
        BareGenericEvent bare = ev.getBare();
        ev.setOrigin(bare.getOrigin());
        ev.setId(bare.getId());
        ev.setChannelId(bare.getChannelId());
        return ev;
    }

    private Long sid;
    private String id;
    private String origin;
    private String channelId;
    private JsonObject data;
    private String receivedFrom;
    private Instant receivedAt;
    private String fetchedFrom;
    private Instant fetchedAt;
    private boolean processed;
    private Instant processedOn;
    private boolean present;
    private boolean valid;
    private boolean allowed;
    private Long orderMajor;
    private Long orderMinor;

    private transient BareGenericEvent bare;

    public ChannelEvent() {
    }

    public ChannelEvent(long sid) {
        this.sid = sid;
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

    public String getId() {
        if (Objects.isNull(id)) {
            id = getBare().getId();
        }

        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getChannelId() {
        if (Objects.isNull(channelId)) {
            channelId = getBare().getChannelId();
        }

        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public JsonObject getData() {
        return data;
    }

    public BareGenericEvent getBare() {
        if (Objects.isNull(bare)) {
            bare = GsonUtil.fromJson(getData(), BareGenericEvent.class);
        }

        return bare;
    }

    public void setData(JsonObject data) {
        this.data = data;
        bare = null;
    }

    public String getReceivedFrom() {
        return receivedFrom;
    }

    public void setReceivedFrom(String receivedFrom) {
        this.receivedFrom = receivedFrom;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getFetchedFrom() {
        return fetchedFrom;
    }

    public void setFetchedFrom(String fetchedFrom) {
        this.fetchedFrom = fetchedFrom;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
        if (processed) {
            setProcessedOn(Instant.now());
        } else {
            setValid(false);
            setAllowed(false);
        }
    }

    public Instant getProcessedOn() {
        if (!isProcessed()) {
            throw new IllegalStateException();
        }

        return processedOn;
    }

    public void setProcessedOn(Instant processedOn) {
        this.processedOn = processedOn;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public Long getOrderMajor() {
        return orderMajor;
    }

    public void setOrderMajor(Long orderMajor) {
        this.orderMajor = orderMajor;
    }

    public Long getOrderMinor() {
        return orderMinor;
    }

    public void setOrderMinor(Long orderMinor) {
        this.orderMinor = orderMinor;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

}
