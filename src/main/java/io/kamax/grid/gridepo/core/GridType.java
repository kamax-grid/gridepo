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

public class GridType extends Type {

    public static class ID extends Type {

        public static class Local extends Type {

            public static class Username extends Type {

                public static final String Id = "username";

                private Username(String parent) {
                    super(parent, Username.Id);
                }

            }

            public static final String Id = "local";

            public Username username() {
                return new Username(getId());
            }

            private Local(String parent) {
                super(parent, Local.Id);
            }

        }

        public static final String Id = "id";

        public Local local() {
            return new Local(getId());
        }

        public String local(String child) {
            return local().make(child);
        }

        private ID(String parent) {
            super(parent, ID.Id);
        }

    }

    public static final String Id = "g";

    public static GridType get() {
        return new GridType();
    }

    public static String of(String child) {
        return get().make(child);
    }

    public static ID id() {
        return new ID(GridType.Id);
    }

    private GridType() {
        super(GridType.Id);
    }

}
