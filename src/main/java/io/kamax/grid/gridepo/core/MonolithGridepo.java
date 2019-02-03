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

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.channel.ChannelManager;
import io.kamax.grid.gridepo.core.crypto.KeyManager;
import io.kamax.grid.gridepo.core.crypto.MemoryKeyStore;
import io.kamax.grid.gridepo.core.crypto.SignManager;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.core.store.Store;
import org.apache.commons.lang3.StringUtils;

public class MonolithGridepo implements Gridepo {

    private GridepoConfig cfg;
    private Store store;
    private ChannelManager chMgr;

    public MonolithGridepo(GridepoConfig cfg) {
        this.cfg = cfg;

        // FIXME use ServiceLoader
        if (StringUtils.equals("memory", cfg.getStorage().getType())) {
            store = new MemoryStore();
        } else {
            throw new IllegalArgumentException("Unknown storage: " + cfg.getStorage().getType());
        }

        DataServerManager dsmgr = new DataServerManager();
        KeyManager keyMgr = new KeyManager(new MemoryKeyStore());
        SignManager signMgr = new SignManager(keyMgr);
        EventService evSvc = new EventService(cfg.getDomain(), signMgr);

        chMgr = new ChannelManager(cfg, evSvc, store, dsmgr);
    }

    @Override
    public void start() {
        if (StringUtils.isBlank(cfg.getDomain())) {
            throw new RuntimeException("Configuration: domain cannot be blank");
        }
    }

    @Override
    public void stop() {

    }

    @Override
    public String getDomain() {
        return cfg.getDomain();
    }

    @Override
    public ChannelManager getChannelManager() {
        return chMgr;
    }

    @Override
    public UserSession login(String username, String password) {
        return null;
    }

}
