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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

public class UserDirectorySearchHandler extends ClientApiHandler {

    private final Gridepo g;

    public UserDirectorySearchHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        g.withToken(exchange.getAccessToken());

        JsonArray results = new JsonArray();
        String term = GsonUtil.getStringOrThrow(exchange.parseJsonObject(), "search_term");
        if (StringUtils.length(term) > 1 && StringUtils.startsWith(term, "@") && !StringUtils.contains(term, ":")) {
            String uId = term + ":" + g.getDomain();
            JsonObject result = new JsonObject();
            result.addProperty("user_id", uId);
            result.addProperty("display_name", term.substring(1) + " (Auto-complete server)");
            results.add(result);
        }

        JsonObject body = new JsonObject();
        body.addProperty("limited", true); // So data is not cached
        body.add("results", results);

        exchange.respond(body);
    }

}
