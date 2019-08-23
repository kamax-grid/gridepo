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

package io.kamax.grid.gridepo.core.identity.store.memory;

import io.kamax.grid.gridepo.config.IdentityConfig;
import io.kamax.grid.gridepo.core.auth.Credentials;
import io.kamax.grid.gridepo.core.identity.GenericThreePid;
import io.kamax.grid.gridepo.core.identity.IdentityStore;
import io.kamax.grid.gridepo.core.identity.IdentityStoreSupplier;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

public class MemoryIdentityStoreSupplier implements IdentityStoreSupplier {

    private static final Logger log = LoggerFactory.getLogger(MemoryIdentityStoreSupplier.class);

    @Override
    public Set<String> getSupportedTypes() {
        return Collections.singleton("memory.internal");
    }

    @Override
    public IdentityStore build(IdentityConfig.Store cfg) {
        String id = GsonUtil.getStringOrThrow(GsonUtil.makeObj(cfg.getConfig()), "connection");
        MemoryStore store = MemoryStore.get(id);
        long uLid = store.addUser("a");
        store.addThreePid(uLid, new GenericThreePid("g.id.local.username", "a"));
        store.addCredentials(uLid, new Credentials("g.auth.id.password", "a"));
        return store;
    }

}
