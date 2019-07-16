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

public class Type {

    private String id;

    public Type(String id) {
        this.id = id;
    }

    public Type(String parent, String id) {
        this(parent + "." + id);
    }

    public String getId() {
        return id;
    }

    public boolean matches(String otherId) {
        return StringUtils.equals(otherId, id);
    }

    public boolean isParentOf(String otherId) {
        return StringUtils.startsWith(otherId, getNamespace());
    }

    public String getNamespace() {
        return getId() + ".";
    }

    public String make(String child) {
        return getNamespace() + child;
    }

}
