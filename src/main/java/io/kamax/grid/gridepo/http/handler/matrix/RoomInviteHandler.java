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
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.EntityGUID;
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

public class RoomInviteHandler extends ClientApiHandler {

    private final Gridepo g;

    public RoomInviteHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        UserSession s = g.withToken(exchange.getAccessToken());
        JsonObject body = exchange.parseJsonObject();

        String mId = exchange.getPathVariable("roomId");
        if (StringUtils.isEmpty(mId)) {
            throw new IllegalArgumentException("Missing Room ID in path");
        }
        String cId = ProtocolEventMapper.forChannelIdFromMatrixToGrid(mId);

        EntityGUID uAl;
        if (body.has("medium")) {
            // This is 3PID invite, generic mapping to alias
            String network = GsonUtil.getStringOrThrow(body, "medium");
            String address = GsonUtil.getStringOrThrow(body, "address");
            uAl = new EntityGUID(network, address);
        } else if (body.has("user_id")) {
            // This is a Matrix ID invite, mapping to alias
            uAl = new EntityGUID("matrix", GsonUtil.getStringOrThrow(body, "user_id"));
        } else {
            // Nothing else is possible at this time, throwing error
            throw new IllegalArgumentException("Not a Matrix ID or 3PID invite");
        }

        s.inviteToChannel(cId, uAl);
        exchange.respondJson("{}");
    }

}
