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
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.grid.ProtocolEventMapper;
import io.kamax.grid.gridepo.network.matrix.http.handler.ClientApiHandler;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

public class RoomDirectoryAddHandler extends ClientApiHandler {

    private final Gridepo g;

    public RoomDirectoryAddHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        UserSession s = g.withToken(exchange.getAccessToken());

        String rAlias = exchange.getPathVariable("roomAlias");
        if (StringUtils.isEmpty(rAlias)) {
            throw new IllegalArgumentException("Missing Room Alias in path");
        }

        if (StringUtils.isBlank(rAlias)) {
            throw new IllegalArgumentException("Room alias cannot be blank");
        }

        JsonObject body = exchange.parseJsonObject();
        String rId = GsonUtil.getStringOrThrow(body, "room_id");
        if (StringUtils.isBlank(rId)) {
            throw new IllegalArgumentException("Room alias cannot be blank");
        }

        String cAlias = rAlias.replaceFirst(":", "@");
        ChannelID cId = ChannelID.parse(ProtocolEventMapper.forChannelIdFromMatrixToGrid(rId));

        s.addChannelAlias(cAlias, cId);

        exchange.respondJson("{}");
    }

}
