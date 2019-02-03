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

import java.util.Objects;

public class EntityID {

    private String sigil;
    private String id;
    private String complete;

    public EntityID(String sigil, String id) {
        this.sigil = Objects.requireNonNull(sigil);
        this.id = Objects.requireNonNull(id);
        this.complete = sigil + id;
    }

    public String getSigil() {
        return sigil;
    }

    public String getId() {
        return id;
    }

    public String full() {
        return complete;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityID)) return false;

        EntityID entityID = (EntityID) o;

        if (!sigil.equals(entityID.sigil)) return false;
        if (!id.equals(entityID.id)) return false;
        return complete.equals(entityID.complete);
    }

    @Override
    public int hashCode() {
        int result = sigil.hashCode();
        result = 31 * result + id.hashCode();
        result = 31 * result + complete.hashCode();
        return result;
    }

}
