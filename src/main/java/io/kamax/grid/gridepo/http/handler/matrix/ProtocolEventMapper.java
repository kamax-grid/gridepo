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
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.channel.event.*;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class ProtocolEventMapper {

    private static final String WildcardType = "*";
    private static final Logger log = KxLog.make(ProtocolEventMapper.class);

    private static Map<String, Function<ChannelEvent, JsonObject>> g2mMappers = new HashMap<>();
    private static Map<String, Function<JsonObject, JsonObject>> m2gMappers = new HashMap<>();

    static {
        setupGridToMatrix();
        setupMatrixToGrid();
    }

    public static void setupGridToMatrix() {
        // Known types mapper
        g2mMappers.put(ChannelEventType.Create.getId(), ev -> {
            BareCreateEvent gEv = GsonUtil.get().fromJson(ev.getData(), BareCreateEvent.class);
            JsonObject mEv = mapCommon(ev.getId().full(), gEv, new JsonObject());
            mEv.addProperty("type", "m.room.create");
            mEv.addProperty("state_key", "");
            String creator = forUserIdFromGridToMatrix(gEv.getContent().getCreator());
            GsonUtil.getObj(mEv, "content").addProperty("creator", creator);
            return mEv;
        });

        g2mMappers.put(ChannelEventType.Member.getId(), ev -> {
            BareMemberEvent gEv = GsonUtil.get().fromJson(ev.getData(), BareMemberEvent.class);
            JsonObject mEv = mapCommon(ev.getId().full(), gEv, new JsonObject());
            mEv.addProperty("type", "m.room.member");
            GsonUtil.getObj(mEv, "content").addProperty("membership", gEv.getContent().getAction());
            return mEv;
        });

        g2mMappers.put(ChannelEventType.Power.getId(), ev -> {
            BarePowerEvent gEv = GsonUtil.get().fromJson(ev.getData(), BarePowerEvent.class);
            BarePowerEvent.Content c = gEv.getContent();

            JsonObject mEvCu = new JsonObject();
            c.getUsers().forEach((id, pl) -> mEvCu.addProperty(forUserIdFromGridToMatrix(id), pl));

            JsonObject mEvC = new JsonObject();
            mEvC.addProperty("ban", c.getMembership().getBan());
            mEvC.addProperty("invite", c.getMembership().getInvite());
            mEvC.addProperty("kick", c.getMembership().getKick());
            mEvC.addProperty("state_default", c.getDef().getState());
            mEvC.addProperty("events_default", c.getDef().getEvent());
            mEvC.addProperty("users_default", c.getDef().getUser());
            mEvC.add("users", mEvCu);

            JsonObject mEv = mapCommon(ev.getId().full(), gEv, new JsonObject());
            mEv.addProperty("type", "m.room.power_levels");
            mEv.addProperty("state_key", "");
            mEv.add("content", mEvC);

            return mEv;
        });

        // Default mapper
        g2mMappers.put(WildcardType, gEv -> {
            JsonObject mEv = mapCommon(gEv.getId().full(), gEv.getBare(), new JsonObject());

            String type = gEv.getBare().getType();
            if (type.startsWith("g.c.s.")) {
                type = type.replace("g.c.s.", "m.room.");
            }

            if (type.startsWith("g.c.p.")) {
                type = type.replace("g.c.p.", "m.room.");
            }

            if (type.startsWith("g.c.e.")) {
                type = type.replace("g.c.e.", "m.room.");
            }

            mEv.addProperty("type", type);
            return mEv;
        });
    }

    private static void mapCommon(BareEvent ev, JsonObject json) {
        GsonUtil.findString(json, "room_id").ifPresent(rId -> ev.setChannelId(forChannelIdFromMatrixToGrid(rId)));
        GsonUtil.findString(json, "event_id").ifPresent(ProtocolEventMapper::forEventIdFromMatrixToGrid);
        GsonUtil.findLong(json, "origin_server_ts").ifPresent(ev::setTimestamp);
        GsonUtil.findString(json, "sender").ifPresent(ProtocolEventMapper::forUserIdFromMatrixToGrid);
        GsonUtil.findString(json, "state_key").ifPresent(ev::setScope);
    }

    public static void setupMatrixToGrid() {
        m2gMappers.put("m.room.name", json -> {
            BareNameEvent nEv = new BareNameEvent();
            mapCommon(nEv, json);

            GsonUtil.findObj(json, "content")
                    .flatMap(c -> GsonUtil.findString(c, "name"))
                    .ifPresent(n -> nEv.getContent().setName(n));

            return nEv.getJson();
        });
    }

    private static JsonObject mapCommon(String id, BareEvent gEv, JsonObject mEv) {
        mEv.addProperty("room_id", forChannelIdFromGridToMatrix(gEv.getChannelId()));
        mEv.addProperty("event_id", forEventIdFromGridToMatrix(id));
        mEv.addProperty("origin_server_ts", gEv.getTimestamp());
        mEv.addProperty("sender", forUserIdFromGridToMatrix(gEv.getSender()));
        mEv.add("content", GsonUtil.makeObj(gEv.getContent()));

        String scope = gEv.getScope();
        if (Objects.nonNull(scope)) {
            if (scope.startsWith("@")) {
                scope = forUserIdFromGridToMatrix(scope);
            }

            mEv.addProperty("state_key", scope);
        }

        return mEv;
    }

    public static JsonObject convert(ChannelEvent gEv) {
        String type = gEv.getBare().getType();

        // We check if we have a mapper for this type
        if (g2mMappers.containsKey(type)) {
            return g2mMappers.get(type).apply(gEv);
        }

        // We use default
        return g2mMappers.get(WildcardType).apply(gEv);
    }

    public static JsonObject convert(JsonObject mEv) {
        String type = StringUtils.defaultIfBlank(GsonUtil.getStringOrNull(mEv, "type"), "");
        if (m2gMappers.containsKey(type)) {
            return m2gMappers.get(type).apply(mEv);
        }

        log.warn("Not transforming unsupported Matrix event type {}", type);
        return mEv;
    }

    public static String forUserIdFromMatrixToGrid(String mId) {
        String gId = mId.substring(1);
        String[] parts = gId.split(":", 1);
        gId = UserID.from(parts[0], parts[1]).full();
        log.debug("User ID: Matrix -> Grid: {} -> {}", mId, gId);
        return gId;
    }

    public static String forUserIdFromGridToMatrix(String gId) {
        String mId = gId.substring(1);
        mId = new String(Base64.decodeBase64(mId), StandardCharsets.UTF_8);
        mId = "@" + mId.replace("@", ":");
        log.debug("User ID: Grid -> Matrix: {} -> {}", mId, gId);
        return mId;
    }

    public static String forEventIdFromMatrixToGrid(String mId) {
        String gId = mId.substring(1);
        String[] parts = gId.split(":", 1);
        gId = EventID.from(parts[0], parts[1]).full();
        log.debug("Event ID: Matrix -> Grid: {} -> {}", mId, gId);
        return gId;
    }

    public static String forEventIdFromGridToMatrix(String gId) {
        String mId = gId.substring(1);
        mId = new String(Base64.decodeBase64(mId), StandardCharsets.UTF_8);
        mId = "$" + mId.replace("@", ":");
        log.debug("Event ID: Grid -> Matrix: {} -> {}", gId, mId);
        return mId;
    }

    public static String forChannelIdFromGridToMatrix(String gId) {
        String mId = gId.substring(1);
        mId = new String(Base64.decodeBase64(mId), StandardCharsets.UTF_8);
        String[] parts = mId.split("@");
        mId = "!" + Base64.encodeBase64URLSafeString(parts[0].getBytes(StandardCharsets.UTF_8)) + ":" + parts[1];
        log.debug("Channel ID: Grid -> Matrix: {} -> {}", gId, mId);
        return mId;
    }

    public static String forChannelIdFromMatrixToGrid(String mId) {
        String gId = mId.substring(1);
        String[] parts = gId.split(":", 2);
        gId = ChannelID.from(parts[0], parts[1]).full();
        log.debug("Channel ID: Matrix -> Grid: {} -> {}", mId, gId);
        return gId;
    }

}
