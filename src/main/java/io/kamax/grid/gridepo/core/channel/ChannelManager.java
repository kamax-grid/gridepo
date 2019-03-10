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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgos;
import io.kamax.grid.gridepo.core.channel.algo.v0.ChannelAlgoV0_0;
import io.kamax.grid.gridepo.core.channel.event.BareCreateEvent;
import io.kamax.grid.gridepo.core.channel.event.BareEvent;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {

    private Gridepo g;
    private SignalBus bus;
    private EventService evSvc;
    private Store store;
    private DataServerManager dsmgr;

    private Map<String, Channel> channels = new ConcurrentHashMap<>();

    public ChannelManager(Gridepo g, SignalBus bus, EventService evSvc, Store store, DataServerManager dsmgr) {
        this.g = g;
        this.bus = bus;
        this.evSvc = evSvc;
        this.store = store;
        this.dsmgr = dsmgr;
    }

    private String generateId() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(Instant.now().toEpochMilli() - 1546297200000L); // TS since 2019-01-01T00:00:00Z to keep IDs short
        byte[] tsBytes = buffer.array();
        String localpart = new String(tsBytes, StandardCharsets.UTF_8) + RandomStringUtils.randomAlphanumeric(2);

        return ChannelID.from(localpart, g.getConfig().getDomain()).full();
    }

    public Channel createChannel(String creator) {
        return createChannel(creator, g.getConfig().getChannel().getCreation().getVersion());
    }

    public Channel createChannel(String creator, String version) {
        ChannelAlgo algo = ChannelAlgos.get(version);

        ChannelDao dao = new ChannelDao();
        dao.setId(generateId());
        dao = store.saveChannel(dao); // FIXME rollback creation in case of failure, or use transaction

        Channel ch = new Channel(dao, g.getOrigin(), algo, evSvc, store, dsmgr, bus);
        channels.put(ch.getId(), ch);

        List<BareEvent> createEvents = algo.getCreationEvents(creator);
        createEvents.stream()
                .map(ch::makeEvent)
                .map(ev -> evSvc.finalize(ev))
                .map(ch::injectLocal)
                .filter(auth -> !auth.isAuthorized())
                .findAny().ifPresent(auth -> {
            throw new RuntimeException("Room creation failed because of initial event(s) being rejected: " + auth.getReason());
        });

        return ch;
    }

    public Channel create(String from, JsonObject seedJson, List<JsonObject> stateJson) {
        BareMemberEvent ev = GsonUtil.fromJson(seedJson, BareMemberEvent.class);
        ChannelDao dao = new ChannelDao();
        dao.setId(ev.getChannelId());
        dao = store.saveChannel(dao);

        BareCreateEvent createEv = GsonUtil.fromJson(stateJson.get(0), BareCreateEvent.class);
        String version = StringUtils.defaultIfEmpty(createEv.getContent().getVersion(), ChannelAlgoV0_0.Version);
        Channel ch = new Channel(dao, g.getOrigin(), ChannelAlgos.get(version), evSvc, store, dsmgr, bus);
        List<ChannelEventAuthorization> auths = ch.injectRemote(from, stateJson);

        auths.stream().filter(auth -> !auth.isAuthorized()).findFirst().ifPresent(auth -> {
            throw new IllegalArgumentException("State event " + auth.getEventId() + " is not authorized: " + auth.getReason());
        });

        ChannelEventAuthorization auth = ch.injectRemote(from, seedJson);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException("Seed is not allowed as per state: " + auth.getReason());
        }

        channels.put(ch.getId(), ch);
        return ch;
    }

    public Optional<Channel> find(String id) {
        return Optional.ofNullable(channels.get(id));
    }

    public Channel get(String id) {
        return find(id).orElseThrow(() -> new ObjectNotFoundException("Channel", id));
    }

}
