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

public class EntityAlias {

    public static final String Delimiter = "@";

    private String sigill;
    private String local;
    private String network;
    private String raw;
    private String full;

    public EntityAlias(String sigill, String local, String network) {
        this.sigill = Objects.requireNonNull(sigill);
        this.local = Objects.requireNonNull(local).toLowerCase();
        this.network = Objects.requireNonNull(network).toLowerCase();

        init();
    }

    private void init() {
        raw = local() + Delimiter + network();
        full = sigill() + raw();
    }

    public String sigill() {
        return sigill;
    }

    public String local() {
        return local;
    }

    public String network() {
        return network;
    }

    public String raw() {
        return raw;
    }

    public String full() {
        return full;
    }

}
