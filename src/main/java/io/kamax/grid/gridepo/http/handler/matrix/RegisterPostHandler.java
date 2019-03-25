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
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import java.util.Optional;

public class RegisterPostHandler extends ClientApiHandler {

    private final Gridepo g;

    public RegisterPostHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        String kind = StringUtils.defaultIfEmpty(exchange.getQueryParameter("kind"), "user");
        if (!StringUtils.equals("user", kind)) {
            throw new NotImplementedException("Registration with a kind other than user");
        }

        JsonObject req = exchange.parseJsonObject();

        Optional<JsonObject> authOpt = GsonUtil.findObj(req, "auth");
        if (!authOpt.isPresent()) {
            JsonObject res = RegisterGetHandler.makeFlows();
            res.addProperty("errcode", "M_INVALID_PARAM");
            res.addProperty("error", "Auth data is missing");
            exchange.respond(HttpStatus.SC_BAD_REQUEST, res);
            return;
        }
        JsonObject auth = authOpt.get();

        Optional<String> typeOpt = GsonUtil.findString(auth, "type");
        if (!typeOpt.isPresent()) {
            JsonObject res = RegisterGetHandler.makeFlows();
            res.addProperty("errcode", "M_INVALID_PARAM");
            res.addProperty("error", "Auth data type is missing");
            exchange.respond(HttpStatus.SC_BAD_REQUEST, res);
            return;
        }
        String type = typeOpt.get();

        if (!StringUtils.equals("m.login.password", type)) {
            throw new IllegalArgumentException("Type " + type + " is not valid");
        }

        String password = GsonUtil.getStringOrNull(auth, "password");
        String username = GsonUtil.getStringOrThrow(req, "username");

        g.getIdentity().register(username, password);

        UserSession session = g.login(username, password);

        JsonObject reply = new JsonObject();
        reply.addProperty("user_id", "@" + session.getUser().getUsername() + ":" + g.getDomain());
        reply.addProperty("access_token", session.getAccessToken());
        reply.addProperty("device_id", RandomStringUtils.randomAlphanumeric(8));

        // Required for some clients who fail if not present, even if not mandatory and deprecated.
        // https://github.com/Nheko-Reborn/mtxclient/issues/7
        reply.addProperty("home_server", g.getDomain());

        exchange.respondJson(reply);
    }

}
