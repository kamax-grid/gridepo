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
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.grid.ProtocolEventMapper;
import io.kamax.grid.gridepo.network.matrix.http.handler.ClientApiHandler;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

public class SendRoomStateHandler extends ClientApiHandler {

    private final Gridepo g;

    public SendRoomStateHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        UserSession session = g.withToken(exchange.getAccessToken());

        String rId = exchange.getPathVariable("roomId");
        String evType = exchange.getPathVariable("type");
        String stateKey = StringUtils.defaultIfBlank(exchange.getPathVariable("stateKey"), "");

        JsonObject content = exchange.parseJsonObject();
        JsonObject mEv = new JsonObject();
        mEv.addProperty("room_id", rId);
        mEv.addProperty("type", evType);
        mEv.addProperty("state_key", stateKey);
        mEv.add("content", content);

        JsonObject gEv = ProtocolEventMapper.forEventConvertToGrid(mEv);
        String cId = ProtocolEventMapper.forChannelIdFromMatrixToGrid(rId);
        String evId = ProtocolEventMapper.forEventIdFromGridToMatrix(session.send(cId, gEv));
        exchange.respondJson(GsonUtil.makeObj("event_id", evId));
    }

}
