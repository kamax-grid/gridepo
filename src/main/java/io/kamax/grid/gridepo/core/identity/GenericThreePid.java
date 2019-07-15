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

package io.kamax.grid.gridepo.core.identity;

import java.util.Objects;

public class GenericThreePid implements ThreePid {

    private String medium;
    private String address;

    public GenericThreePid(ThreePid tpid) {
        this(tpid.getMedium(), tpid.getAddress());
    }

    public GenericThreePid(String medium, String address) {
        this.medium = medium;
        this.address = address;
    }

    @Override
    public String getMedium() {
        return medium;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GenericThreePid)) return false;
        GenericThreePid that = (GenericThreePid) o;
        return getMedium().equals(that.getMedium()) &&
                getAddress().equals(that.getAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMedium(), getAddress());
    }

    @Override
    public String toString() {
        return medium + ":" + address;
    }

}
