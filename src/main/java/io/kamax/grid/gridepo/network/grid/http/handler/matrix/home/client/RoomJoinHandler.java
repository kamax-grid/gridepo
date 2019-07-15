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

package io.kamax.grid.gridepo.network.grid.http.handler.matrix.home.client;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.ChannelAlias;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.grid.ProtocolEventMapper;
import io.kamax.grid.gridepo.network.matrix.http.handler.ClientApiHandler;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

public class RoomJoinHandler extends ClientApiHandler {

    private final Gridepo g;

    public RoomJoinHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        UserSession s = g.withToken(exchange.getAccessToken());

        String mIdOrAlias = exchange.getPathVariable("roomId");
        if (StringUtils.isEmpty(mIdOrAlias)) {
            throw new IllegalArgumentException("Missing Room ID in path");
        }

        if (mIdOrAlias.startsWith(ChannelID.Sigill)) {
            handleRoomAlias(exchange, s, mIdOrAlias);
        } else if (mIdOrAlias.startsWith("!")) {
            handleRoomId(exchange, s, mIdOrAlias);
        } else {
            throw new IllegalArgumentException("Invalid Room ID/Alias in path: " + mIdOrAlias);
        }

    }

    private void handleRoomAlias(Exchange exchange, UserSession s, String mAlias) {
        ChannelAlias cAlias = ProtocolEventMapper.forChannelAliasFromMatrixToGrid(mAlias);
        Channel c = s.joinChannel(cAlias);
        exchange.respond(GsonUtil.makeObj("room_id", ProtocolEventMapper.fromGridToMatrix(c.getId())));
    }

    private void handleRoomId(Exchange exchange, UserSession s, String mId) {
        String cId = ProtocolEventMapper.forChannelIdFromMatrixToGrid(mId);

        JsonObject body = exchange.parseJsonObject();
        if (body.has("third_party_signed")) {
            throw new NotImplementedException("3PIDs invites to join rooms");
        }

        s.joinChannel(cId);

        exchange.respond(GsonUtil.makeObj("room_id", mId));
    }

}
