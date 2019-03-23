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

package io.kamax.grid.gridepo.http.handler.grid.server;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.ChannelAlias;
import io.kamax.grid.gridepo.core.ServerSession;
import io.kamax.grid.gridepo.core.channel.ChannelLookup;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.http.handler.grid.server.io.ChannelLookupResponse;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.HashSet;

public class ChannelDirectoryLookupHandler extends ServerApiHandler {

    private final Gridepo g;

    public ChannelDirectoryLookupHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        ServerSession s = g.forServer(exchange.authenticate());

        JsonObject body = exchange.parseJsonObject();
        String aliasRaw = GsonUtil.getStringOrThrow(body, "alias");
        ChannelAlias cAlias = ChannelAlias.parse(aliasRaw);
        ChannelLookup lookup = s.lookup(cAlias).orElseThrow(() -> new ObjectNotFoundException("Channel alias", cAlias.full()));

        ChannelLookupResponse response = new ChannelLookupResponse();
        response.setId(lookup.getId().full());
        response.setServers(new HashSet<>());
        lookup.getServers().forEach(id -> response.getServers().add(id.full()));

        exchange.respondJson(response);
    }

}
