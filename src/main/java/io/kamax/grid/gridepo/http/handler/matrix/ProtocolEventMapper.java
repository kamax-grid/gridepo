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
import io.kamax.grid.gridepo.core.channel.event.*;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ProtocolEventMapper {

    private static Map<String, Function<ChannelEvent, JsonObject>> mappers = new HashMap<>();

    static {
        // Known types mapper
        mappers.put(ChannelEventType.Create.getId(), ev -> {
            BareCreateEvent gEv = GsonUtil.get().fromJson(ev.getData(), BareCreateEvent.class);
            JsonObject mEv = mapCommon(ev.getId(), gEv, new JsonObject());
            mEv.addProperty("type", "m.room.create");
            String creator = forUserIdFromGridToMatrix(gEv.getContent().getCreator());
            GsonUtil.getObj(mEv, "content").addProperty("creator", creator);
            return mEv;
        });

        mappers.put(ChannelEventType.Member.getId(), ev -> {
            BareMemberEvent gEv = GsonUtil.get().fromJson(ev.getData(), BareMemberEvent.class);
            JsonObject mEv = mapCommon(ev.getId(), gEv, new JsonObject());
            mEv.addProperty("type", "m.room.member");
            GsonUtil.getObj(mEv, "content").addProperty("membership", gEv.getContent().getAction());
            return mEv;
        });

        // Default mapper
        mappers.put("*", gEv -> {
            JsonObject mEv = mapCommon(gEv.getId(), gEv.getBare(), new JsonObject());

            String type = gEv.getBare().getType();
            if (type.startsWith("g.c.")) {
                type = type.replace("g.c.", "m.room.");
            }

            mEv.addProperty("type", type);
            return mEv;
        });
    }

    private static JsonObject mapCommon(String id, BareEvent gEv, JsonObject mEv) {
        mEv.addProperty("room_id", gEv.getChannelId());
        mEv.addProperty("event_id", forEventIdFromGridToMatrix(id));
        mEv.addProperty("origin_server_ts", gEv.getTimestamp());
        mEv.addProperty("sender", forUserIdFromGridToMatrix(gEv.getSender()));
        mEv.add("content", GsonUtil.makeObj(gEv.getContent()));

        String scope = gEv.getScope();
        if (StringUtils.isNotEmpty(scope)) {
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
        if (mappers.containsKey(type)) {
            return mappers.get(type).apply(gEv);
        }

        // We use default
        return mappers.get("*").apply(gEv);
    }

    public static String forUserIdFromGridToMatrix(String gId) {
        String mId = gId.substring(1);
        mId = new String(Base64.decodeBase64(mId), StandardCharsets.UTF_8);
        mId = "@" + mId.replace("@", ":");
        return mId;
    }

    public static String forEventIdFromGridToMatrix(String gId) {
        String mId = gId.substring(1);
        mId = new String(Base64.decodeBase64(mId), StandardCharsets.UTF_8);
        mId = "$" + mId.replace("@", ":");
        return mId;
    }

}
