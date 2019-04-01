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
import io.kamax.grid.gridepo.core.ChannelAlias;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgos;
import io.kamax.grid.gridepo.core.channel.algo.v0.ChannelAlgoV0_0;
import io.kamax.grid.gridepo.core.channel.event.BareCreateEvent;
import io.kamax.grid.gridepo.core.channel.event.BareEvent;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.structure.ApprovalExchange;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServer;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.exception.EntityUnreachableException;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {

    private static final Logger log = KxLog.make(ChannelManager.class);

    private Gridepo g;
    private SignalBus bus;
    private EventService evSvc;
    private Store store;
    private DataServerManager dsmgr;

    private Map<ChannelID, Channel> channels = new ConcurrentHashMap<>();

    public ChannelManager(Gridepo g, SignalBus bus, EventService evSvc, Store store, DataServerManager dsmgr) {
        this.g = g;
        this.bus = bus;
        this.evSvc = evSvc;
        this.store = store;
        this.dsmgr = dsmgr;
    }

    private Channel fromDao(ChannelDao dao) {
        // FIXME get version from somewhere
        return new Channel(dao, g.getOrigin(), ChannelAlgos.get(null), evSvc, store, dsmgr, bus);
    }

    private ChannelID generateId() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(Instant.now().toEpochMilli() - 1546297200000L); // TS since 2019-01-01T00:00:00Z to keep IDs short
        byte[] tsBytes = buffer.array();
        String localpart = new String(tsBytes, StandardCharsets.UTF_8) + RandomStringUtils.randomAlphanumeric(2);

        return ChannelID.from(localpart, g.getDomain());
    }

    public Channel createChannel(String creator) {
        return createChannel(creator, g.getConfig().getChannel().getCreation().getVersion());
    }

    public Channel createChannel(String creator, String version) {
        ChannelAlgo algo = ChannelAlgos.get(version);

        ChannelDao dao = new ChannelDao(generateId());
        dao = store.saveChannel(dao); // FIXME rollback creation in case of failure, or use transaction

        Channel ch = new Channel(dao, g.getOrigin(), algo, evSvc, store, dsmgr, bus);
        channels.put(ch.getId(), ch);

        List<BareEvent> createEvents = algo.getCreationEvents(creator);
        createEvents.stream()
                .map(ch::makeEvent)
                .map(ev -> evSvc.finalize(ev))
                .map(ch::offer)
                .filter(auth -> !auth.isAuthorized())
                .findAny().ifPresent(auth -> {
            throw new RuntimeException("Room creation failed because of initial event(s) being rejected: " + auth.getReason());
        });

        return ch;
    }

    public Channel create(String from, JsonObject seedJson, List<JsonObject> stateJson) {
        BareMemberEvent ev = GsonUtil.fromJson(seedJson, BareMemberEvent.class);
        ChannelDao dao = new ChannelDao(ChannelID.from(ev.getChannelId()));
        dao = store.saveChannel(dao);

        BareCreateEvent createEv = GsonUtil.fromJson(stateJson.get(0), BareCreateEvent.class);
        String version = StringUtils.defaultIfEmpty(createEv.getContent().getVersion(), ChannelAlgoV0_0.Version);
        Channel ch = new Channel(dao, g.getOrigin(), ChannelAlgos.get(version), evSvc, store, dsmgr, bus);

        ChannelEventAuthorization auth = ch.inject(from, seedJson, stateJson);
        if (!auth.isAuthorized()) {
            throw new ForbiddenException("Seed is not allowed as per state: " + auth.getReason());
        }

        channels.put(ch.getId(), ch);
        return ch;
    }

    public synchronized Optional<Channel> find(ChannelID cId) {
        if (!channels.containsKey(cId)) {
            store.findChannel(cId).ifPresent(channelDao -> channels.put(cId, fromDao(channelDao)));
        }

        return Optional.ofNullable(channels.get(cId));
    }

    public Channel get(ChannelID cId) {
        return find(cId).orElseThrow(() -> new ObjectNotFoundException("Channel", cId));
    }

    public Channel get(String id) {
        return get(ChannelID.from(id));
    }

    public Channel join(ChannelAlias cAlias, UserID uId) {
        ChannelLookup data = g.getChannelDirectory().lookup(cAlias, true)
                .orElseThrow(() -> new ObjectNotFoundException("Channel alias", cAlias.full()));

        BareMemberEvent bEv = new BareMemberEvent();
        bEv.setChannelId(data.getId().full());
        bEv.setSender(uId.full());
        bEv.setScope(uId.full());
        bEv.getContent().setAction(ChannelMembership.Join);

        Optional<Channel> cOpt = find(data.getId());
        if (cOpt.isPresent()) {
            Channel c = cOpt.get();
            if (c.getView().getAllServers().stream().anyMatch(g::isLocal)) {
                // We are joined, so we can make our own event

                ChannelEventAuthorization auth = c.makeAndOffer(bEv.getJson());
                if (!auth.isAuthorized()) {
                    throw new ForbiddenException(auth.getReason());
                }

                return c;
            }
        }

        // Couldn't join locally, let's try remotely
        if (data.getServers().isEmpty()) {
            // We have no peer we can use to join
            throw new EntityUnreachableException();
        }

        for (DataServer srv : dsmgr.get(data.getServers(), true)) {
            String origin = srv.getId().full();
            try {
                ApprovalExchange ex = srv.approveJoin(g.getOrigin().full(), bEv);
                JsonObject seed = g.getEventService().finalize(ex.getObject());
                if (cOpt.isPresent()) {
                    Channel c = cOpt.get();

                    // The room already exists, so we need to add the join to it
                    c.offer(origin, ex.getContext().getState());
                    c.offer(origin, seed);

                    return c;
                } else {
                    return create(origin, seed, ex.getContext().getState());
                }
            } catch (ForbiddenException e) {
                log.warn("{} refused to sign our join request to {} because: {}", srv.getId(), data.getId(), e.getReason());
            }
        }

        throw new ForbiddenException("No resident server approved the join request");
    }

}
