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
import io.kamax.grid.gridepo.core.*;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.event.*;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.channel.structure.InviteApprovalRequest;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServer;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class Channel {

    private static final Logger log = KxLog.make(Channel.class);

    private ChannelDao dao;
    private ServerID origin;

    private Store store;
    private DataServerManager srvMgr;
    private ChannelAlgo algo;
    private EventService evSvc;
    private SignalBus bus;

    private BarePowerEvent.Content defaultPls;
    private ChannelView view;

    public Channel(long sid, ChannelID id, ServerID origin, ChannelAlgo algo, EventService evSvc, Store store, DataServerManager srvMgr, SignalBus bus) {
        this(new ChannelDao(sid, id), origin, algo, evSvc, store, srvMgr, bus);
    }

    public Channel(ChannelDao dao, ServerID origin, ChannelAlgo algo, EventService evSvc, Store store, DataServerManager srvMgr, SignalBus bus) {
        this.dao = dao;
        this.origin = origin;
        this.algo = algo;
        this.evSvc = evSvc;
        this.store = store;
        this.srvMgr = srvMgr;
        this.bus = bus;

        init();
    }

    private void init() {
        // FIXME we need to resolve the extremities as a timeline, get the HEAD and its state
        List<ChannelEvent> extremities = store.getForwardExtremities(getSid()).stream().map(store::getEvent).collect(Collectors.toList());
        EventID head = extremities.stream().max(Comparator.comparingLong(ChannelEvent::getSid)).map(ChannelEvent::getId).orElse(null);
        ChannelState state = extremities.stream()
                .max(Comparator.comparingLong(ev -> ev.getBare().getDepth()))
                .map(ChannelEvent::getLid)
                .map(store::getStateForEvent)
                .orElseGet(ChannelState::empty);
        view = new ChannelView(origin, head, state);
        log.info("Channel {}: Loaded saved state SID {}", getSid(), view.getState().getSid());
    }

    public long getSid() {
        return dao.getSid();
    }

    public ChannelID getId() {
        return dao.getId();
    }

    public ServerID getDomain() {
        return origin;
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

    public ChannelTimeline getTimeline() {
        return new ChannelTimeline(dao.getSid(), store);
    }

    public List<Long> getExtremitySids() {
        return store.getForwardExtremities(getSid());
    }

    public List<EventID> getExtremityIds() {
        return getExtremitySids().stream()
                .map(store::getEventId)
                .collect(Collectors.toList());
    }

    public List<ChannelEvent> getExtremities() {
        return getExtremitySids().stream()
                .map(evId -> store.getEvent(evId))
                .collect(Collectors.toList());
    }

    public JsonObject makeEvent(BareEvent ev) {
        return makeEvent(ev.getJson());
    }

    public JsonObject makeEvent(JsonObject obj) {
        // In case we make the event for an approval, we do not overwrite an existing origin
        if (!obj.has(EventKey.Origin)) {
            obj.addProperty(EventKey.Origin, origin.full());
        }

        obj.addProperty(EventKey.ChannelId, getId().full());
        obj.addProperty(EventKey.Id, algo.generateEventId(origin.tryDecode().orElse(origin.base())).full());
        obj.addProperty(EventKey.Timestamp, Instant.now().toEpochMilli());

        List<ChannelEvent> exts = getExtremities();
        List<String> extIds = exts.stream()
                .map(ChannelEvent::getId)
                .map(EventID::full)
                .collect(Collectors.toList());
        long depth = exts.stream()
                .map(ChannelEvent::getBare)
                .mapToLong(BareEvent::getDepth)
                .max()
                .orElse(algo.getBaseDepth()) + 1;

        obj.add(EventKey.PrevEvents, GsonUtil.asArray(extIds));
        obj.addProperty(EventKey.Depth, depth);

        return obj;
    }

    public boolean isUsable(ChannelEvent ev) {
        return ev.getMeta().isPresent() && ev.getMeta().isProcessed();
    }

    public ChannelEventAuthorization authorize(JsonObject ev) {
        return algo.authorize(getView().getState(), null, ev);
    }

    private synchronized ChannelEvent processIfNotAlready(EventID evId) {
        process(evId, true, false);
        return store.getEvent(getId(), evId);
    }

    private synchronized ChannelEventAuthorization process(EventID evId, boolean recursive, boolean force) {
        ChannelEvent ev = store.getEvent(getId(), evId);
        if (!ev.getMeta().isPresent() || (ev.getMeta().isProcessed() && !force)) {
            return new ChannelEventAuthorization.Builder(evId)
                    .authorize(ev.getMeta().isPresent() && ev.getMeta().isValid() && ev.getMeta().isAllowed(), "From previous computation");
        }

        return process(ev, recursive);
    }

    public synchronized ChannelEventAuthorization process(ChannelEvent ev, boolean recursive) {
        return process(ev, recursive, false);
    }

    public synchronized ChannelEventAuthorization process(ChannelEvent ev, boolean recursive, boolean isSeed) {
        log.info("Processing event {} in channel {}", ev.getId(), ev.getChannelId());
        ChannelEventAuthorization.Builder b = new ChannelEventAuthorization.Builder(ev.getId());
        BareGenericEvent bEv = ev.getBare();
        ev.getMeta().setPresent(true);

        ChannelState state = getView().getState();
        long maxParentDepth = bEv.getDepth() - 1;
        if (!isSeed) {
            maxParentDepth = bEv.getPreviousEvents().stream()
                    .map(EventID::from)
                    .map(pEvId -> {
                        if (recursive) {
                            return processIfNotAlready(pEvId);
                        } else {
                            return store.findEvent(getId(), pEvId).orElseGet(() -> ChannelEvent.forNotFound(getSid(), pEvId));
                        }
                    })
                    .filter(this::isUsable)
                    .max(Comparator.comparingLong(pEv -> pEv.getBare().getDepth()))
                    .map(pEv -> pEv.getBare().getDepth())
                    .orElse(Long.MIN_VALUE);
            if (bEv.getPreviousEvents().isEmpty()) {
                maxParentDepth = algo.getBaseDepth();
            }
        }

        if (maxParentDepth == Long.MIN_VALUE) {
            b.deny("No parent event is found or valid, marking event as unauthorized");
        } else {
            long expectedDepth = maxParentDepth + 1;
            if (expectedDepth > bEv.getDepth()) {
                b.invalid("Depth is " + bEv.getDepth() + " but was expected to be at least " + expectedDepth);
            } else {
                ChannelEventAuthorization auth = algo.authorize(state, ev.getId(), ev.getData()); // FIXME do it on the parents
                b.authorize(auth.isAuthorized(), auth.getReason());
                if (auth.isAuthorized()) {
                    state = state.apply(ev);
                }
            }
        }

        ChannelEventAuthorization auth = b.get();
        ev.getMeta().setSeed(isSeed);
        ev.getMeta().setValid(auth.isValid());
        ev.getMeta().setAllowed(isSeed || auth.isAuthorized());
        ev.getMeta().setProcessed(true);

        log.info("Event {} is allowed? {}", ev.getId(), ev.getMeta().isAllowed());
        if (!ev.getMeta().isAllowed()) {
            log.info("Because: {}", auth.getReason());
        }

        ev = store.saveEvent(ev);
        state = store.getState(store.insertIfNew(getSid(), state));
        store.map(ev.getLid(), state.getSid());
        store.addToStream(ev.getLid());

        if (ev.getMeta().isAllowed()) {
            List<Long> toRemove = ev.getBare().getPreviousEvents().stream()
                    .map(id -> store.findEventLid(getId(), EventID.from(id)))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            List<Long> toAdd = Collections.singletonList(ev.getLid());
            store.updateForwardExtremities(getSid(), toRemove, toAdd);
            view = new ChannelView(origin, ev.getId(), state);
        }

        bus.forTopic(SignalTopic.Channel).publish(new ChannelMessageProcessed(store.getEvent(ev.getLid()), auth));

        return auth;
    }

    public void backfill(ChannelEvent ev, List<EventID> earliest, long minDepth) {
        if (ev.getBare().getPreviousEvents().isEmpty()) {
            log.info("Channel Event {} has no previous event, skipping backfill", ev.getId());
            return;
        }

        backfill(ev.getPreviousEvents(), earliest, minDepth);
    }

    public void backfill(List<EventID> latest, List<EventID> earliest, long minDepth) {
        BlockingQueue<EventID> remaining = new LinkedBlockingQueue<>(latest);
        Stack<ChannelEvent> events = new Stack<>();

        log.info("{}: Need to check backfill on {} events", getDomain(), remaining.size());
        while (!remaining.isEmpty()) {
            EventID evId = remaining.poll();
            ChannelEvent chEv = ChannelEvent.forNotFound(getSid(), evId);

            Optional<ChannelEvent> storeEv = store.findEvent(getId(), evId);
            if (storeEv.isPresent()) {
                chEv = storeEv.get();
                if (chEv.getMeta().isPresent()) {
                    log.info("Channel Event {} is already present, no backfill", chEv.getId());
                    continue;
                }
            }

            log.info("Trying to backfill on {}", evId);
            Set<ServerID> srvIds = getView().getOtherServers();
            if (srvIds.isEmpty()) {
                log.warn("Backfill needed but no other server in state, something will break");
            }

            List<DataServer> servers = srvMgr.get(srvIds, true);
            if (servers.isEmpty()) {
                log.warn("Backfill needed but no available server, something will break");
            }

            log.info("Found {} available servers to ask for the event", servers.size());
            for (DataServer srv : servers) {
                log.info("Asking {} for event {} in channel {}", srv.getId(), evId, getId());
                Optional<JsonObject> data = srv.getEvent(getDomain().full(), getId(), evId);
                if (data.isPresent()) {
                    log.info("Found event {} at {}", evId, srv.getId());
                    chEv = ChannelEvent.from(getSid(), evId, data.get());
                    chEv.setData(data.get());
                    chEv.getMeta().setFetchedFrom(srv.getId().full());
                    chEv.getMeta().setFetchedAt(Instant.now());
                    chEv.getMeta().setValid(false);
                    chEv.getMeta().setAllowed(false);
                    chEv.getMeta().setProcessed(false);

                    List<EventID> parents = chEv.getPreviousEvents();
                    if (chEv.getBare().getDepth() > minDepth && Collections.disjoint(earliest, parents)) {
                        log.info("Found {} events still needing backfill", parents.size());
                        remaining.addAll(parents);
                    }

                    break;
                } else {
                    log.info("Did not find event {} at {}", evId, srv.getId());
                }
            }

            chEv.getMeta().setPresent(Objects.nonNull(chEv.getData()));
            events.push(chEv);
        }

        events.forEach(chEv -> store.saveEvent(chEv));
    }

    public synchronized List<ChannelEventAuthorization> offer(List<ChannelEvent> events, boolean isSeed) {
        ChannelState state = getView().getState();

        List<ChannelEventAuthorization> auths = new ArrayList<>();
        events.stream().sorted(Comparator.comparingLong(ev -> ev.getBare().getDepth())).forEach(event -> {
            log.info("Channel {} - Processing injection of Event {}", getId(), event.getId());
            Optional<ChannelEvent> evStore = store.findEvent(getId(), event.getId());

            if (evStore.isPresent() && evStore.get().getMeta().isPresent()) {
                log.info("Event {} is known, skipping", event.getId());
                // We already have the event, we skip
                return;
            }

            event.getMeta().setPresent(Objects.nonNull(event.getData()));

            // We check if the event is valid and allowed for the current state before considering processing it
            ChannelEventAuthorization auth = algo.authorize(state, event.getId(), event.getData());
            event.getMeta().setValid(auth.isValid());
            event.getMeta().setAllowed(auth.isAuthorized());

            if (!auth.isAuthorized()) {
                // TODO switch to debug later
                log.info("Event {} not authorized: {}", auth.getEventId(), auth.getReason());
            }

            // Still need to process
            event.getMeta().setProcessed(false);
            if (!isSeed) {
                long minDepth = getExtremities().stream()
                        .map(ChannelEvent::getBare)
                        .min(Comparator.comparingLong(BareEvent::getDepth))
                        .map(BareEvent::getDepth)
                        .orElse(0L);

                backfill(event, getExtremityIds(), minDepth);
            } else {
                log.info("Skipping backfill on seed event {}", event.getId());
            }

            auth = process(event, true, isSeed);

            auths.add(auth);
        });

        return auths;
    }

    public List<ChannelEventAuthorization> offer(String from, List<JsonObject> events) {
        return offer(from, events, false);
    }

    public List<ChannelEventAuthorization> offer(String from, List<JsonObject> events, boolean isSeed) {
        return offer(events.stream().map(raw -> {
            ChannelEvent ev = ChannelEvent.from(getSid(), raw);
            ev.getMeta().setSeed(isSeed);
            ev.getMeta().setReceivedFrom(from);
            ev.getMeta().setReceivedAt(Instant.now());
            return ev;
        }).collect(Collectors.toList()), isSeed);
    }

    public ChannelEventAuthorization offer(String from, JsonObject event) {
        return offer(from, Collections.singletonList(event)).get(0);
    }

    public ChannelEventAuthorization offer(JsonObject ev) {
        ChannelEvent cEv = ChannelEvent.from(getSid(), ev);
        cEv.getMeta().setReceivedAt(Instant.now());

        return offer(Collections.singletonList(cEv), false).get(0);
    }

    public ChannelEventAuthorization offer(BareEvent ev) {
        return offer(evSvc.finalize(makeEvent(ev)));
    }

    public ChannelEventAuthorization inject(String from, JsonObject event, List<JsonObject> state) {
        state.stream().map(raw -> {
            ChannelEvent ev = ChannelEvent.from(getSid(), raw);
            ev.getMeta().setReceivedFrom(from);
            ev.getMeta().setReceivedAt(Instant.now());
            return ev;
        }).forEach(ev -> {
            ChannelEventAuthorization auth = process(ev, false, true);
            if (!auth.isAuthorized()) {
                throw new RuntimeException("Seed state received from " + from + " is not valid");
            }
        });

        ChannelEvent ev = ChannelEvent.from(getSid(), event);
        ev.getMeta().setReceivedFrom(from);
        ev.getMeta().setReceivedAt(Instant.now());
        return process(ev, false, true);
    }

    public ChannelEventAuthorization makeAndOffer(JsonObject ev) {
        return offer(makeEvent(ev));
    }

    public ChannelState getState(ChannelEvent ev) {
        return store.getStateForEvent(ev.getLid());
    }

    public ChannelEvent invite(String inviter, EntityGUID invitee) {
        // FIXME export to solve resolver class (Back to the Identity!)
        String localpart;
        String domain;
        if ("email".equals(invitee.getNetwork())) {
            String[] data = invitee.getAddress().split("@", 2);
            localpart = data[0];
            domain = data[1];
        } else if ("matrix".equals(invitee.getNetwork())) {
            String[] data = invitee.getAddress().split(":", 2);
            localpart = data[0].substring(1);
            domain = data[1];
        } else if ("grid".equals(invitee.getNetwork())) {
            // FIXME We need an address class to properly parse this
            String[] data = invitee.getAddress().substring(1).split("@", 2);
            localpart = data[0];
            domain = data[1];
        } else {
            throw new NotImplementedException("Alias network " + invitee.getNetwork());
        }

        UserID uId = UserID.from(localpart, domain);
        ServerID remoteId = ServerID.from(domain);

        BareMemberEvent ev = new BareMemberEvent();
        ev.setSender(inviter);
        ev.setScope(uId.full());
        ev.getContent().setAction(ChannelMembership.Invite);
        JsonObject evFull = evSvc.finalize(makeEvent(ev));

        if (!origin.equals(remoteId)) {
            // This is a remote invite

            List<JsonObject> state = getView().getState().getEvents().stream()
                    .sorted(Comparator.comparingLong(b -> b.getBare().getDepth()))
                    .map(ChannelEvent::getData)
                    .collect(Collectors.toList());

            InviteApprovalRequest request = new InviteApprovalRequest();
            request.setObject(evFull);
            request.getContext().setState(state);

            evFull = srvMgr.get(remoteId).approveInvite(origin.full(), request);
        }

        ChannelEventAuthorization result = offer(evFull);
        if (!result.isAuthorized()) {
            throw new ForbiddenException(result.getReason());
        }

        return store.getEvent(getId(), result.getEventId());
    }

}
