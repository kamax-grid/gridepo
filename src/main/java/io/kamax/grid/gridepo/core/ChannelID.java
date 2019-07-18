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

public class ChannelID extends EntityID {

    public static final String Sigill = "#";

    public static ChannelID parse(String id) {
        if (!StringUtils.startsWith(id, Sigill)) {
            throw new IllegalArgumentException("Does not start with " + Sigill);
        }

        return fromRaw(id.substring(1));
    }

    public static ChannelID fromRaw(String rawId) {
        return new ChannelID(rawId);
    }

    public static ChannelID from(String localpart, String namespace) {
        return new ChannelID(encode(localpart + Delimiter + namespace));
    }

    public ChannelID(String id) {
        super(Sigill, id);
    }

}
