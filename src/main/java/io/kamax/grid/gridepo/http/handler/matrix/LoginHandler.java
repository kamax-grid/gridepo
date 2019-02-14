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
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.http.handler.ClientApiHandler;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.RandomStringUtils;

public class LoginHandler extends ClientApiHandler {

    private final Gridepo srv;

    public LoginHandler(Gridepo srv) {
        this.srv = srv;
    }

    @Override
    protected void handle(Exchange exchange) {
        JsonObject body = exchange.parseJsonObject();
        String username = GsonUtil.getStringOrThrow(body, "user");
        String password = GsonUtil.getStringOrThrow(body, "password");

        UserSession session = srv.login(username, password);

        JsonObject reply = new JsonObject();
        reply.addProperty("user_id", "@" + session.getUser().getUsername() + ":" + srv.getDomain());
        reply.addProperty("access_token", session.getAccessToken());
        reply.addProperty("device_id", RandomStringUtils.randomAlphanumeric(8));

        exchange.respondJson(reply);
    }

}
