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
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgos;
import io.kamax.grid.gridepo.core.channel.event.BareEvent;
import io.kamax.grid.gridepo.core.crypto.KeyManager;
import io.kamax.grid.gridepo.core.crypto.MemoryKeyStore;
import io.kamax.grid.gridepo.core.crypto.SignManager;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.core.store.Store;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.util.List;

public class MonolithGridepo implements Gridepo {

    private GridepoConfig cfg;
    private Store store;
    private EventService evSvc;
    private DataServerManager dsmgr;

    // FIXME make nice method to generate an ID from timestamp
    public byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x - 1546297200000L); // Remote TS from 2019-01-01T00:00:00Z
        return buffer.array();
    }

    public MonolithGridepo(GridepoConfig cfg) {
        this.cfg = cfg;

        // FIXME use ServiceLoader
        if (StringUtils.equals("memory", cfg.getStorage().getType())) {
            store = new MemoryStore();
        } else {
            throw new IllegalArgumentException("Unknown storage: " + cfg.getStorage().getType());
        }

        this.dsmgr = new DataServerManager();
        KeyManager keyMgr = new KeyManager(new MemoryKeyStore());
        SignManager signMgr = new SignManager(keyMgr);
        evSvc = new EventService(cfg.getDomain(), signMgr);
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
    public Channel createChannel(String creator) {
        return createChannel(creator, cfg.getChannel().getCreation().getVersion());
    }

    @Override
    public Channel createChannel(String creator, String version) {
        ChannelAlgo algo = ChannelAlgos.get(version);

        String chId = "#" + Base64.encodeBase64URLSafeString(longToBytes(System.currentTimeMillis())) + RandomStringUtils.randomAlphanumeric(2);
        ChannelDao dao = new ChannelDao();
        dao.setId(chId);
        dao = store.saveChannel(dao); // FIXME rollback creation in case of failure, or use transaction

        Channel ch = new Channel(dao, cfg.getDomain(), algo, store, dsmgr);
        List<BareEvent> createEvents = algo.getCreationEvents(creator);
        createEvents.stream()
                .map(ch::makeEvent)
                .map(ev -> evSvc.finalize(ev))
                .map(ch::inject)
                .filter(auth -> !auth.isAuthorized())
                .findAny().ifPresent(auth -> {
            throw new RuntimeException("Room creation failed because of initial event(s) being rejected: " + auth.getReason());
        });

        return ch;
    }

}
