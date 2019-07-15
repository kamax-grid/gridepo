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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.ChannelAlias;
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.core.channel.ChannelLookup;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.grid.ProtocolEventMapper;
import io.kamax.grid.gridepo.network.matrix.http.handler.ClientApiHandler;

public class RoomAliasLookupHandler extends ClientApiHandler {

    private final Gridepo g;

    public RoomAliasLookupHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        UserSession s = g.withToken(exchange.getAccessToken());

        String rAlias = exchange.getPathVariable("roomAlias");
        ChannelAlias cAlias = ProtocolEventMapper.forChannelAliasFromMatrixToGrid(rAlias);

        ChannelLookup lookup = s.lookup(cAlias).orElseThrow(() -> new ObjectNotFoundException("Room alias", rAlias));

        JsonArray servers = new JsonArray();
        lookup.getServers().forEach(id -> id.tryDecodeDns().ifPresent(servers::add));

        JsonObject response = new JsonObject();
        response.addProperty("room_id", ProtocolEventMapper.fromGridToMatrix(lookup.getId()));
        response.add("servers", servers);

        exchange.respond(response);
    }

}
