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

import org.apache.commons.lang3.StringUtils;

public class ChannelAlias extends EntityAlias {

    public static final String Sigill = "#";

    public static ChannelAlias parse(String alias) {
        if (!StringUtils.startsWith(alias, Sigill)) {
            throw new IllegalArgumentException("Does not start with " + Sigill);
        }

        return fromRaw(alias.substring(1));
    }

    public static ChannelAlias fromRaw(String alias) {
        if (!StringUtils.contains(alias, Delimiter)) {
            throw new IllegalArgumentException("Does not contain delimiter " + Delimiter);
        }

        String[] data = alias.split("@", 2);

        return new ChannelAlias(data[0], data[1]);
    }

    public ChannelAlias(String local, String network) {
        super(Sigill, local, network);
    }

    @Override
    public String toString() {
        return full();
    }

}
