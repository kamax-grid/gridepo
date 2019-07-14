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

package io.kamax.grid.gridepo.core.auth;

import java.util.Arrays;

public class Credentials {

    private String type;
    private char[] data;

    public Credentials(String type, char[] data) {
        this.type = type;
        this.data = data;
    }

    public Credentials(String type, String data) {
        this(type, data.toCharArray());
    }

    public String getType() {
        return type;
    }

    protected void setType(String type) {
        this.type = type;
    }

    public char[] getData() {
        return data;
    }

    public char[] getDataAndClear() {
        char[] toReturn = data;
        clear();
        return toReturn;
    }

    protected void setData(char[] data) {
        this.data = data;
    }

    public void clear() {
        Arrays.fill(data, (char) 0);
    }

}
