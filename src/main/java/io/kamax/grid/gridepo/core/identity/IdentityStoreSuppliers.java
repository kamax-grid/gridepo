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

import java.util.*;

public class IdentityStoreSuppliers {

    private static ServiceLoader<IdentityStoreSupplier> loader;

    static {
        reload();
    }

    private static synchronized void reload() {
        if (Objects.isNull(loader)) {
            loader = ServiceLoader.load(IdentityStoreSupplier.class);
        } else {
            loader.reload();
        }
    }

    public static synchronized Optional<IdentityStoreSupplier> get(String type) {
        List<IdentityStoreSupplier> suppliersAll = new ArrayList<>();
        loader.forEach(suppliersAll::add);
        return suppliersAll.stream().filter(s -> s.getSupportedTypes().contains(type)).findFirst();
    }

}
