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

package io.kamax.grid.gridepo.network.grid.http.handler.grid.data.channel;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.ServerSession;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.grid.http.handler.grid.GridApiHandler;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.Optional;

public class GetEventHandler extends GridApiHandler {

    private final Gridepo g;

    public GetEventHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        ServerSession s = g.forServer(exchange.authenticate());

        ChannelID cId = ChannelID.parse(exchange.getPathVariable("channelId"));
        EventID eId = EventID.parse(exchange.getPathVariable("eventId"));
        Optional<ChannelEvent> cEv = s.getEvent(cId, eId);
        JsonObject data = cEv.flatMap(ev -> {
            if (!ev.getMeta().isPresent()) {
                return Optional.empty();
            }

            if (!ev.getMeta().isProcessed()) {
                return Optional.empty();
            }

            return Optional.of(ev.getData());
        }).orElseThrow(() -> new ObjectNotFoundException("Event", eId));

        exchange.respond(GsonUtil.makeObj("event", data));
    }

}
