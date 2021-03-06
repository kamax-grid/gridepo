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

package io.kamax.grid.gridepo.network.grid.http.handler.grid.data;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.ServerSession;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.structure.ApprovalExchange;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.grid.http.handler.grid.GridApiHandler;

public class DoApproveJoin extends GridApiHandler {

    private final Gridepo g;

    public DoApproveJoin(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        ServerSession s = g.forServer(exchange.authenticate());

        BareMemberEvent evToApprove = exchange.parseJsonTo(BareMemberEvent.class);
        ApprovalExchange reply = s.approveJoin(evToApprove);

        exchange.respondJson(reply);
    }

}
