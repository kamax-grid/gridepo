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

import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.http.handler.SaneHandler;
import io.kamax.grid.gridepo.util.GsonUtil;

public class FiltersPostHandler extends SaneHandler {

    @Override
    protected void handle(Exchange exchange) {
        exchange.respond(GsonUtil.makeObj("filter_id", Long.toString(System.currentTimeMillis())));
    }

}
