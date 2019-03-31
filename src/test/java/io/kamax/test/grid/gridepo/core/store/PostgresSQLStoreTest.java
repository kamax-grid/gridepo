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

package io.kamax.test.grid.gridepo.core.store;

import io.kamax.grid.gridepo.config.StorageConfig;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.core.store.postgres.PostgreSQLStore;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;

import java.util.Objects;

import static org.junit.Assume.assumeTrue;

public class PostgresSQLStoreTest extends StoreTest {

    private static StorageConfig cfg;
    private static PostgreSQLStore pStore;

    @BeforeClass
    public static void beforeClass() {
        String cfgJson = System.getenv("GRIDEPO_TEST_STORE_POSTGRESQL_CONFIG");
        assumeTrue(StringUtils.isNotBlank(cfgJson));
        cfg = GsonUtil.parse(cfgJson, StorageConfig.class);
    }

    @Override
    protected Store getNewStore() {
        if (Objects.isNull(pStore)) {
            pStore = new PostgreSQLStore(cfg);
        }

        return pStore;
    }

}
