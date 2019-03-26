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

import io.kamax.grid.gridepo.exception.*;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.util.KxLog;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.time.Instant;

public abstract class ServerApiHandler implements HttpHandler {

    private transient final Logger log = KxLog.make(ServerApiHandler.class);

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.startBlocking();

        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
        } else {
            Exchange ex = new Exchange(exchange);
            try {
                handle(ex);
            } catch (IllegalArgumentException e) {
                ex.respond(HttpStatus.SC_BAD_REQUEST, "G_INVALID_PARAM", e.getMessage());
                log.debug("Trigger:", e);
            } catch (MissingTokenException e) {
                ex.respond(HttpStatus.SC_UNAUTHORIZED, "G_MISSING_TOKEN", e.getMessage());
                log.debug("Trigger:", e);
            } catch (InvalidTokenException e) {
                ex.respond(HttpStatus.SC_UNAUTHORIZED, "G_UNKNOWN_TOKEN", e.getMessage());
                log.debug("Trigger:", e);
            } catch (ForbiddenException e) {
                ex.respond(HttpStatus.SC_FORBIDDEN, "G_FORBIDDEN", e.getReason());
                log.debug("Trigger:", e);
            } catch (ObjectNotFoundException e) {
                ex.respond(HttpStatus.SC_NOT_FOUND, "G_NOT_FOUND", e.getMessage());
                log.debug("Trigger:", e);
            } catch (NotImplementedException e) {
                ex.respond(HttpStatus.SC_NOT_IMPLEMENTED, "G_NOT_IMPLEMENTED", e.getMessage());
                log.debug("Trigger:", e);
            } catch (RemoteServerException e) {
                ex.respond(HttpStatus.SC_BAD_GATEWAY, e.getCode(), e.getReason());
                log.debug("Trigger:", e);
            } catch (RuntimeException e) {
                log.error("Unknown error when handling {} - CHECK THE SURROUNDING LOG LINES TO KNOW THE ACTUAL CAUSE!", exchange.getRequestURL(), e);
                ex.respond(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.buildErrorBody("G_UNKNOWN",
                        StringUtils.defaultIfBlank(
                                e.getMessage(),
                                "An internal server error occurred. Contact your system administrator with Log Reference " +
                                        Instant.now().toEpochMilli()
                        )
                ));
            } finally {
                exchange.endExchange();
            }

            // TODO refactor the common code from the various API handlers into a single class
            if (log.isInfoEnabled()) {
                String protocol = exchange.getConnection().getTransportProtocol().toUpperCase();
                String vhost = exchange.getHostName();
                String remotePeer = exchange.getConnection().getPeerAddress(InetSocketAddress.class).getAddress().getHostAddress();
                String method = exchange.getRequestMethod().toString();
                String path = exchange.getRequestURI();
                int statusCode = exchange.getStatusCode();
                long writtenByes = exchange.getResponseBytesSent();

                if (StringUtils.isEmpty(ex.getError())) {
                    log.info("Request - {} - {} - {} - {} {} - {} - {}", protocol, vhost, remotePeer, method, path, statusCode, writtenByes);
                } else {
                    log.info("Request - {} - {} - {} - {} {} - {} - {} - {}", protocol, vhost, remotePeer, method, path, statusCode, writtenByes, ex.getError());
                }
            }
        }
    }

    protected abstract void handle(Exchange exchange);

}
