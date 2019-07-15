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

package io.kamax.grid.gridepo.http.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.exception.MissingTokenException;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Exchange {

    private static final Logger log = KxLog.make(Exchange.class);

    private final HttpServerExchange exchange;
    private String error;

    public Exchange(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    // TODO refactor into a MatrixClientExchange
    public String getAccessToken() {
        if (exchange.getRequestHeaders().contains(HttpString.tryFromString("Authorization"))) {
            String value = exchange.getRequestHeaders().getFirst("Authorization");
            if (!StringUtils.startsWith(value, "Bearer ")) {
                throw new MissingTokenException("Authorization type is not recognized. Must be \"Bearer \"");
            }

            return value.substring("Bearer ".length());
        }

        if (exchange.getQueryParameters().containsKey("access_token")) {
            String accessToken = getQueryParameter("access_token");
            if (StringUtils.isEmpty(accessToken)) {
                throw new MissingTokenException("Access token in query parameter cannot be empty");
            }

            return accessToken;
        }

        throw new MissingTokenException("No access token given");
    }

    // TODO refactor into a ServerExchange
    public String authenticate() {
        String value = exchange.getRequestHeaders().getFirst("X-Grid-Remote-ID");
        if (StringUtils.isBlank(value)) {
            throw new MissingTokenException("Remote header is not set");
        }

        return value;
    }

    public String getQueryParameter(String name) {
        return getQueryParameter(exchange.getQueryParameters(), name);
    }

    public String getQueryParameter(Map<String, Deque<String>> parms, String name) {
        try {
            String raw = parms.getOrDefault(name, new LinkedList<>()).peekFirst();
            if (StringUtils.isEmpty(raw)) {
                return raw;
            }

            return URLDecoder.decode(raw, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getPathVariable(String name) {
        return getQueryParameter(name);
    }

    public String getHeader(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public String requireHeader(String name) {
        String header = getHeader(name);
        if (Objects.isNull(header)) {
            throw new IllegalArgumentException("Header '" + name + "' is required");
        }

        return header;
    }

    public String requireHost() {
        return requireHeader("Host");
    }

    public Optional<String> getContentType() {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("Content-Type"));
    }

    public void writeBodyAsUtf8(String body) {
        exchange.getResponseSender().send(body, StandardCharsets.UTF_8);
    }

    public String getBodyUtf8() {
        try {
            return IOUtils.toString(exchange.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T parseJsonTo(Class<T> type) {
        return GsonUtil.get().fromJson(getBodyUtf8(), type);
    }

    public JsonObject parseJsonObject(String key) {
        return GsonUtil.getObj(parseJsonObject(), key);
    }

    public JsonObject parseJsonObject() {
        return GsonUtil.parseObj(getBodyUtf8());
    }

    public void respond(int statusCode, JsonElement bodyJson) {
        if (log.isDebugEnabled()) {
            log.debug("Body: {}", GsonUtil.getPrettyForLog(bodyJson));
        }

        respondJson(statusCode, GsonUtil.get().toJson(bodyJson));
    }

    public void respond(JsonElement bodyJson) {
        respond(200, bodyJson);
    }

    public void respondJson(int status, String body) {
        try {
            exchange.setStatusCode(status);
            exchange.getResponseHeaders().put(HttpString.tryFromString("Content-Type"), "application/json");
            writeBodyAsUtf8(body);
        } catch (IllegalStateException e) {
            // already sent, we ignore
        }
    }

    public void respondJson(String body) {
        respondJson(200, body);
    }

    public void respondJson(Object body) {
        if (log.isDebugEnabled()) {
            log.debug("Body:{}", GsonUtil.getPrettyForLog(body));
        }

        respondJson(GsonUtil.toJson(body));
    }

    public JsonObject buildErrorBody(String errCode, String error) {
        JsonObject obj = new JsonObject();
        obj.addProperty("errcode", setError(errCode));
        obj.addProperty("error", error);
        obj.addProperty("success", false);
        return obj;
    }

    public void respond(int status, String errCode, String error) {
        respond(status, buildErrorBody(setError(errCode), error));
    }

    public String setError(String code) {
        return error = code;
    }

    public String getError() {
        return error;
    }

    public HttpServerExchange getUnderlying() {
        return exchange;
    }

}
