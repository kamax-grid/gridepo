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
import io.kamax.grid.gridepo.http.handler.Exchange;
import org.apache.commons.lang3.StringUtils;

public class RoomInitialSyncHandler extends ClientApiHandler {

    private final Gridepo g;

    public RoomInitialSyncHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        g.withToken(exchange.getAccessToken());

        String rId = exchange.getPathVariable("roomId");
        if (StringUtils.isBlank(rId)) {
            throw new IllegalArgumentException("Room ID is invalid: " + rId);
        }

        JsonObject response = new JsonObject();
        response.addProperty("room_id", rId);
        response.addProperty("membership", "leave");

        exchange.respond(response);
    }

}
