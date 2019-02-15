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

package io.kamax.grid.gridepo.core.channel;

import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgos;
import io.kamax.grid.gridepo.core.channel.event.BareEvent;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {

    private GridepoConfig cfg;
    private EventService evSvc;
    private Store store;
    private DataServerManager dsmgr;

    private Map<String, Channel> channels = new ConcurrentHashMap<>();

    public ChannelManager(GridepoConfig cfg, EventService evSvc, Store store, DataServerManager dsmgr) {
        this.cfg = cfg;
        this.evSvc = evSvc;
        this.store = store;
        this.dsmgr = dsmgr;
    }

    private String generateId() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(Instant.now().toEpochMilli() - 1546297200000L); // TS since 2019-01-01T00:00:00Z to keep IDs short
        byte[] tsBytes = buffer.array();

        String localId = Base64.encodeBase64URLSafeString(tsBytes) + RandomStringUtils.randomAlphanumeric(2);
        String decodedId = localId + "@" + cfg.getDomain();
        return "#" + Base64.encodeBase64URLSafeString(decodedId.getBytes(StandardCharsets.UTF_8));
    }

    public Channel createChannel(String creator) {
        return createChannel(creator, cfg.getChannel().getCreation().getVersion());
    }

    public Channel createChannel(String creator, String version) {
        ChannelAlgo algo = ChannelAlgos.get(version);

        ChannelDao dao = new ChannelDao();
        dao.setId(generateId());
        dao = store.saveChannel(dao); // FIXME rollback creation in case of failure, or use transaction

        Channel ch = new Channel(dao, cfg.getDomain(), algo, store, dsmgr);
        channels.put(ch.getId(), ch);

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

    public Channel get(String id) {
        Channel c = channels.get(id);
        if (Objects.isNull(c)) {
            throw new ObjectNotFoundException("Channel", id);
        }

        return c;
    }

}
