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

import java.util.UUID;

public class RegisterGetHandler extends ClientApiHandler {

    static JsonObject makeFlows() {
        JsonArray stages = new JsonArray();
        stages.add("m.login.password");

        JsonArray flows = new JsonArray();
        flows.add(GsonUtil.makeObj("stages", stages));

        JsonObject body = new JsonObject();
        body.addProperty("session", UUID.randomUUID().toString());
        body.add("flows", flows);

        return body;
    }

    private final Gridepo g;

    public RegisterGetHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        if (!g.getIdentity().canRegister()) {

        } else {
            JsonArray stages = new JsonArray();
            stages.add("m.login.password");

            JsonArray flows = new JsonArray();
            flows.add(GsonUtil.makeObj("stages", stages));

            JsonObject body = new JsonObject();
            body.addProperty("session", UUID.randomUUID().toString());
            body.add("flows", flows);

            exchange.respond(body);
        }
    }

}
