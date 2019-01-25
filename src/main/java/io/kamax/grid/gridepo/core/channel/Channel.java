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
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.event.BareEvent;
import io.kamax.grid.gridepo.core.channel.event.BareGenericEvent;
import io.kamax.grid.gridepo.core.channel.event.BarePowerEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.core.federation.DataServer;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Channel {

    private ChannelDao dao;
    private String domain;

    private Store store;
    private DataServerManager srvMgr;
    private ChannelAlgo algo;

    private BarePowerEvent.Content defaultPls;
    private ChannelView view;

    public Channel(long sid, String id, String domain, ChannelAlgo algo, Store store, DataServerManager srvMgr) {
        this(new ChannelDao(sid, id), domain, algo, store, srvMgr);
    }

    public Channel(ChannelDao dao, String domain, ChannelAlgo algo, Store store, DataServerManager srvMgr) {
        this.dao = dao;
        this.domain = domain;
        this.algo = algo;
        this.store = store;
        this.srvMgr = srvMgr;
        this.view = new ChannelView();
    }

    public long getSid() {
        return dao.getSid();
    }

    public String getId() {
        return dao.getId();
    }

    public String getDomain() {
        return domain;
    }

    public String getVersion() {
        return algo.getVersion();
    }

    public BarePowerEvent.Content getDefaultPls() {
        synchronized (this) {
            if (Objects.isNull(defaultPls)) {
                defaultPls = algo.getDefaultPowers(getView().getState().getCreator());
            }
        }

        return defaultPls;
    }

    public ChannelView getView() {
        return view;
    }

    public List<String> getExtremities() {
        return store.getExtremities(getId());
    }

    public JsonObject makeEvent(BareEvent ev) {
        JsonObject obj = GsonUtil.makeObj(ev);
        return makeEvent(obj);
    }

    public JsonObject makeEvent(JsonObject obj) {
        obj.addProperty(EventKey.Origin, domain);
        obj.addProperty(EventKey.ChannelId, getId());
        obj.addProperty(EventKey.Id, algo.generateEventId(domain));
        obj.addProperty(EventKey.Timestamp, Instant.now().toEpochMilli());

        List<String> exts = getExtremities();
        long depth = exts.stream()
                .map(evId -> store.getEvent(getId(), evId))
                .map(ChannelEvent::getBare)
                .mapToLong(BareEvent::getDepth)
                .max()
                .orElse(algo.getBaseDepth()) + 1;
        obj.add(EventKey.PrevEvents, GsonUtil.asArray(exts));
        obj.addProperty(EventKey.Depth, depth);

        return obj;
    }

    public boolean isUsable(ChannelEvent ev) {
        if (!ev.isProcessed()) {
            throw new IllegalStateException("Event " + getId() + "/" + ev.getId() + " has not been processed");
        }

        return ev.isPresent() && ev.isValid() && ev.isAllowed();
    }

    private synchronized ChannelEvent processIfNotAlready(String evId) {
        process(evId, true, false);
        return store.getEvent(getId(), evId);
    }

    private synchronized ChannelEventAuthorization process(String evId, boolean recursive, boolean force) {
        ChannelEvent ev = store.getEvent(getId(), evId);
        if (ev.isProcessed() && !force) {
            return new ChannelEventAuthorization.Builder(evId)
                    .authorize(ev.isPresent() && ev.isValid() && ev.isAllowed(), "From previous computation");
        }

        return process(ev, recursive);
    }

    public synchronized ChannelEventAuthorization process(ChannelEvent ev, boolean recursive) {
        ChannelEventAuthorization.Builder b = new ChannelEventAuthorization.Builder(ev.getId());
        BareGenericEvent bEv = ev.getBare();
        ev.setPresent(true);

        ChannelState state = getView().getState();
        long maxParentDepth = bEv.getPreviousEvents().stream()
                .map(pEvId -> {
                    if (recursive) {
                        return processIfNotAlready(pEvId);
                    } else {
                        return store.findEvent(getId(), pEvId).orElseGet(() -> ChannelEvent.forNotFound(pEvId));
                    }
                })
                .filter(this::isUsable)
                .max(Comparator.comparingLong(pEv -> pEv.getBare().getDepth()))
                .map(pEv -> pEv.getBare().getDepth())
                .orElse(Long.MIN_VALUE);
        if (bEv.getPreviousEvents().isEmpty()) {
            maxParentDepth = algo.getBaseDepth();
        }


        if (maxParentDepth == Long.MIN_VALUE + 1) {
            b.deny("No parent event is found or valid, marking event as unauthorized");
        } else {
            long expectedDepth = maxParentDepth + 1;
            if (expectedDepth != bEv.getDepth()) {
                b.invalid("Depth is " + bEv.getDepth() + " but was expected to be " + expectedDepth);
            } else {
                ChannelEventAuthorization auth = algo.authorize(state, ev.getData()); // FIXME do it on the parents
                b.authorize(auth.isAuthorized(), auth.getReason());
                if (auth.isAuthorized()) {
                    state = state.apply(ev.getData());
                }
            }
        }

        ChannelEventAuthorization auth = b.get();
        ev.setValid(auth.isValid());
        ev.setAllowed(auth.isAuthorized());
        ev.setProcessed(true);

        ev = store.saveEvent(ev);
        state = store.getState(store.insertIfNew(getId(), state));
        store.map(ev.getSid(), state.getSid());

        List<String> evIds = store.getExtremities(getId());
        for (String prevEv : ev.getBare().getPreviousEvents()) {
            evIds.remove(prevEv);
        }

        evIds.add(ev.getId());
        store.setExtremities(getId(), evIds);

        view = new ChannelView(ev.getId(), state);

        return auth;
    }

    public void backfill(ChannelEvent ev, List<String> earliest, long minDepth) {
        if (ev.getBare().getPreviousEvents().isEmpty()) {
            return;
        }

        backfill(ev.getBare().getPreviousEvents(), earliest, minDepth);
    }

    public void backfill(List<String> latest, List<String> earliest, long minDepth) {
        BlockingQueue<String> remaining = new LinkedBlockingQueue<>(latest);
        Stack<ChannelEvent> events = new Stack<>();

        while (!remaining.isEmpty()) {
            String evId = remaining.poll();
            ChannelEvent chEv = new ChannelEvent();

            Optional<ChannelEvent> storeEv = store.findEvent(getId(), evId);
            if (storeEv.isPresent()) {
                chEv = storeEv.get();
                if (chEv.isPresent()) {
                    continue;
                }
            } else {
                chEv.setChannelId(getId());
                chEv.setId(evId);
                chEv.setProcessed(false);
            }

            for (DataServer srv : srvMgr.get(getView().getState().getServers())) {
                Optional<JsonObject> data = srv.getEvent(getId(), evId);
                if (data.isPresent()) {
                    chEv.setData(data.get());
                    chEv.setFetchedFrom(srv.getDomain());
                    chEv.setFetchedAt(Instant.now());
                    chEv.setValid(false);
                    chEv.setAllowed(false);
                    chEv.setProcessed(false);

                    List<String> parents = chEv.getBare().getPreviousEvents();
                    if (chEv.getBare().getDepth() > minDepth && Collections.disjoint(earliest, parents)) {
                        remaining.addAll(parents);
                    }

                    break;
                }
            }

            chEv.setPresent(Objects.nonNull(chEv.getData()));
            events.push(chEv);
        }

        events.forEach(chEv -> store.saveEvent(chEv));
    }

    public synchronized List<ChannelEventAuthorization> inject(List<ChannelEvent> events) {
        ChannelState state = getView().getState();
        List<String> extremities = getExtremities();

        List<ChannelEventAuthorization> auths = new ArrayList<>();
        events.stream().sorted(Comparator.comparingLong(ev -> ev.getBare().getDepth())).forEach(event -> {
            Optional<ChannelEvent> evStore = store.findEvent(getId(), event.getId());

            if (evStore.isPresent() && evStore.get().isPresent()) {
                // We already have the event, we skip
                return;
            }

            event.setPresent(Objects.nonNull(event.getData()));

            // We check if the event is valid and allowed for the current state before considering processing it
            ChannelEventAuthorization auth = algo.authorize(state, event.getData());
            event.setValid(auth.isValid());
            event.setAllowed(auth.isAuthorized());

            // Still need to process
            event.setProcessed(false);

            long minDepth = extremities.stream()
                    .map(evId -> store.getEvent(getId(), evId))
                    .map(ChannelEvent::getBare)
                    .min(Comparator.comparingLong(BareEvent::getDepth))
                    .map(BareEvent::getDepth)
                    .orElse(0L);

            backfill(event, getExtremities(), minDepth);

            auth = process(event, true);

            auths.add(auth);
        });

        return auths;
    }

    public ChannelEventAuthorization inject(JsonObject ev) {
        ChannelEvent cEv = new ChannelEvent();
        cEv.setData(ev);
        cEv.setReceivedFrom("localhost");
        cEv.setReceivedAt(Instant.now());

        return inject(Collections.singletonList(cEv)).get(0);
    }

}
