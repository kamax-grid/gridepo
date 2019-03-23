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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.channel.event.*;
import io.kamax.grid.gridepo.exception.NotImplementedException;
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

        g2mMappers.put(ChannelEventType.Address.getId(), ev -> {
            BareAddressEvent gEv = GsonUtil.get().fromJson(ev.getData(), BareAddressEvent.class);
            BareAddressEvent.Content c = gEv.getContent();

            JsonObject mEvC = new JsonObject();
            mEvC.addProperty("alias", c.getAlias().replaceFirst("@", ":"));

            JsonObject mEv = mapCommon(ev.getId().full(), gEv, new JsonObject());
            mEv.addProperty("type", "m.room.canonical_alias");
            mEv.add("content", mEvC);

            return mEv;
        });

        g2mMappers.put(ChannelEventType.Alias.getId(), ev -> {
            BareAliasEvent gEv = GsonUtil.get().fromJson(ev.getData(), BareAliasEvent.class);
            BareAliasEvent.Content c = gEv.getContent();

            JsonArray mEvCV = new JsonArray();
            c.getAliases().forEach(alias -> mEvCV.add(alias.replaceFirst("@", ":")));

            JsonObject mEvC = new JsonObject();
            mEvC.add("aliases", mEvCV);

            JsonObject mEv = mapCommon(ev.getId().full(), gEv, new JsonObject());
            mEv.addProperty("type", "m.room.aliases");
            mEv.addProperty("state_key", ServerID.parse(gEv.getScope()).tryDecode().orElseGet(() -> gEv.getScope().substring(1)));
            mEv.add("content", mEvC);

            return mEv;
        });

        g2mMappers.put(ChannelEventType.Message.getId(), ev -> {
            BareMessageEvent gEv = GsonUtil.get().fromJson(ev.getData(), BareMessageEvent.class);
            BareMessageEvent.Content c = gEv.getContent();

            JsonObject mEvC = new JsonObject();
            if (StringUtils.equals("g.text", c.getType())) {
                mEvC.addProperty("msgtype", "m.text");
                mEvC.addProperty("body", c.getBody().get("text/plain"));
                String html = c.getBody().get("text/vnd.grid.foreign.matrix.org.matrix.custom.html");
                if (Objects.nonNull(html)) {
                    mEvC.addProperty("format", "org.matrix.custom.html");
                    mEvC.addProperty("formatted_body", html);
                }
            }

            JsonObject mEv = mapCommon(ev.getId().full(), gEv, new JsonObject());
            mEv.addProperty("type", "m.room.message");
            mEv.add("content", mEvC);

            return mEv;
        });

        // Default mapper
        g2mMappers.put(WildcardType, gEv -> {
            JsonObject mEv = mapCommon(gEv.getId().full(), gEv.getBare(), new JsonObject());

            String type = gEv.getBare().getType();

            if (ChannelEventType.Alias.match(type)) {
                type = "m.room.aliases";
            }

            if (type.startsWith("g.c.s.")) {
                type = type.replace("g.c.s.", "m.room.");
            }

            if (type.startsWith("g.c.p.")) {
                type = type.replace("g.c.p.", "m.room.");
            }

            if (type.startsWith("g.c.e.")) {
                type = type.replace("g.c.e.", "m.room.");
            }

            if (type.startsWith("g.foreign.matrix.")) {
                type = type.substring("g.foreign.matrix.".length());
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
            BareNameEvent gEv = new BareNameEvent();
            mapCommon(gEv, json);

            GsonUtil.findObj(json, "content")
                    .flatMap(c -> GsonUtil.findString(c, "name"))
                    .ifPresent(n -> gEv.getContent().setName(n));

            return gEv.getJson();
        });

        m2gMappers.put("m.room.canonical_alias", json -> {
            BareAddressEvent gEv = new BareAddressEvent();
            mapCommon(gEv, json);

            GsonUtil.findObj(json, "content")
                    .flatMap(c -> GsonUtil.findString(c, "alias"))
                    .ifPresent(alias -> {
                        alias = alias.replaceFirst(":", "@");
                        gEv.getContent().setAlias(alias);
                    });

            return gEv.getJson();
        });

        m2gMappers.put("m.room.aliases", json -> {
            BareAliasEvent gEv = new BareAliasEvent();
            mapCommon(gEv, json);

            GsonUtil.findObj(json, "content")
                    .flatMap(c -> GsonUtil.findArray(c, "aliases"))
                    .ifPresent(v -> gEv.getContent().setAliases(GsonUtil.asSet(v, String.class)));

            return gEv.getJson();
        });

        m2gMappers.put("m.room.message", json -> {
            BareMessageEvent gEv = new BareMessageEvent();
            mapCommon(gEv, json);

            GsonUtil.findObj(json, "content").ifPresent(c -> {
                String msgType = GsonUtil.findString(c, "msgtype").orElseThrow(() -> new IllegalArgumentException("msgtype is not set"));
                if (StringUtils.equals("m.text", msgType)) {
                    gEv.getContent().setType("g.text");
                    GsonUtil.findString(c, "body").ifPresent(v -> gEv.getContent().getBody().put("text/plain", v));
                    GsonUtil.findString(c, "format").ifPresent(f -> {
                        GsonUtil.findString(c, "formatted_body").ifPresent(fc -> {
                            gEv.getContent().getBody().put("text/vnd.grid.foreign.matrix." + f, fc);
                        });
                    });
                } else {
                    throw new NotImplementedException("msgtype " + msgType);
                }
            });

            return gEv.getJson();
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

    public static JsonObject forEventConvertToMatrix(ChannelEvent gEv) {
        String type = gEv.getBare().getType();

        // We check if we have a mapper for this type
        if (g2mMappers.containsKey(type)) {
            return g2mMappers.get(type).apply(gEv);
        }

        // We use default
        return g2mMappers.get(WildcardType).apply(gEv);
    }

    public static JsonObject forEventConvertToGrid(JsonObject mEv) {
        String type = StringUtils.defaultIfBlank(GsonUtil.getStringOrNull(mEv, "type"), "");
        if (m2gMappers.containsKey(type)) {
            return m2gMappers.get(type).apply(mEv);
        } else {
            mEv.addProperty("type", "g.foreign.matrix." + type);
        }

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
        if (StringUtils.isEmpty(gId)) {
            return gId;
        }

        String mId = gId.substring(1);
        mId = new String(Base64.decodeBase64(mId), StandardCharsets.UTF_8);
        String[] parts = mId.split("@");
        mId = "!" + Base64.encodeBase64URLSafeString(parts[0].getBytes(StandardCharsets.UTF_8)) + ":" + parts[1];
        log.debug("Channel ID: Grid -> Matrix: {} -> {}", gId, mId);
        return mId;
    }

    public static String forChannelIdFromMatrixToGrid(String mId) {
        if (StringUtils.isEmpty(mId)) {
            return mId;
        }

        String gId = mId.substring(1);
        String[] parts = gId.split(":", 2);
        gId = ChannelID.from(new String(Base64.decodeBase64(parts[0]), StandardCharsets.UTF_8), parts[1]).full();
        log.debug("Channel ID: Matrix -> Grid: {} -> {}", mId, gId);
        return gId;
    }

}
