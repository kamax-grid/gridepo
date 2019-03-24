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

package io.kamax.grid.gridepo.exception;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.util.GsonUtil;

public class RemoteServerException extends RuntimeException {

    private final String code;
    private final String reason;

    public RemoteServerException(String domain, JsonObject response) {
        this(
                domain,
                GsonUtil.findString(response, "errcode").orElse("G_UNKNOWN"),
                GsonUtil.findString(response, "error").orElse("Server did not return a valid error message")
        );
    }

    public RemoteServerException(String domain, String code, String reason) {
        super("Remote server " + domain + " replied with an error: " + code + " - " + reason);
        this.code = code;
        this.reason = reason;
    }

    public String getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

}
