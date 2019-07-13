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

import java.time.Instant;

public class InternalServerError extends RuntimeException {

    private String reference;
    private String internal;

    private static String getDefaultMessage() {
        return "An internal server error occurred. Contact your administrator with reference Transaction #";
    }

    private static String makeReference() {
        return Long.toString(Instant.now().toEpochMilli());
    }

    public InternalServerError() {
        init(makeReference(), null);
    }

    public InternalServerError(String internalReason) {
        init(makeReference(), internalReason);
    }

    public InternalServerError(Throwable t) {
        super(t);
        init(makeReference(), null);
    }

    public InternalServerError(String internal, Throwable t) {
        this(t);
        init(makeReference(), internal);
    }

    public InternalServerError(String reference, String internal, Throwable t) {
        this(t);
        init(reference, internal);
    }

    private void init(String reference, String internal) {
        this.reference = reference;
        this.internal = internal;
    }

    public String getReference() {
        return reference;
    }

    public String getInternalReason() {
        return internal;
    }

    @Override
    public String getMessage() {
        return getDefaultMessage() + reference;
    }

}
