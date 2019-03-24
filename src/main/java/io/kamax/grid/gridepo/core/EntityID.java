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

package io.kamax.grid.gridepo.core;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

public class EntityID {

    public static final String Delimiter = "@";

    protected static String encode(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            // Nothing we can do about it
            throw new RuntimeException(e);
        }
    }

    private String sigill;
    private String base;
    private String complete;

    public EntityID(String sigill, String id) {
        this.sigill = Objects.requireNonNull(sigill);
        this.base = Objects.requireNonNull(id);
        this.complete = sigill + id;
    }

    public String sigill() {
        return sigill;
    }

    public String base() {
        return base;
    }

    public String full() {
        return complete;
    }

    public Optional<String> tryDecode() {
        try {
            return Optional.of(new String(Base64.getUrlDecoder().decode(base()), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (Objects.isNull(o)) return false;
        if (this == o) return true;
        if (!(o instanceof EntityID)) return false;

        EntityID entityID = (EntityID) o;

        if (!sigill.equals(entityID.sigill)) return false;
        return base.equals(entityID.base);
    }

    @Override
    public int hashCode() {
        int result = sigill.hashCode();
        result = 31 * result + base.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return full();
    }

}
