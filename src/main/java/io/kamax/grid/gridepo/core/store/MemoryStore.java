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

package io.kamax.grid.gridepo.core.store;

import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.exception.AlreadyExistsException;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MemoryStore implements Store {

    private static final Logger log = KxLog.make(MemoryStore.class);

    private AtomicLong chSid = new AtomicLong(0);
    private AtomicLong evSid = new AtomicLong(0);
    private AtomicLong sSid = new AtomicLong(0);

    private Map<String, String> users = new ConcurrentHashMap<>();
    private Map<Long, ChannelDao> channels = new ConcurrentHashMap<>();
    private Map<Long, ChannelEvent> chEvents = new ConcurrentHashMap<>();
    private Map<Long, ChannelState> chStates = new ConcurrentHashMap<>();
    private Map<Long, List<Long>> chExtremities = new ConcurrentHashMap<>();
    private Map<Long, Long> evStates = new ConcurrentHashMap<>();

    private Map<String, ChannelID> chAdrToId = new ConcurrentHashMap<>();
    private Map<ChannelID, Set<String>> chIdToAdr = new ConcurrentHashMap<>();

    private Map<String, Long> evRefToSid = new ConcurrentHashMap<>();

    private String makeRef(ChannelEvent ev) {
        return makeRef(ChannelID.from(ev.getChannelId()), ev.getId());
    }

    private String makeRef(ChannelID cId, EventID eId) {
        return cId + "/" + eId;
    }

    @Override
    public Optional<ChannelDao> findChannel(long cSid) {
        return Optional.ofNullable(channels.get(cSid));
    }

    @Override
    public ChannelDao saveChannel(ChannelDao ch) {
        long sid = chSid.incrementAndGet();
        ch = new ChannelDao(sid, ch.getId());
        channels.put(sid, ch);
        return ch;
    }

    @Override
    public synchronized ChannelEvent saveEvent(ChannelEvent ev) {
        if (!ev.hasSid()) {
            ev.setSid(evSid.incrementAndGet());
        }

        chEvents.put(ev.getSid(), ev);
        evRefToSid.put(makeRef(ev), ev.getSid());

        log.info("Added new channel event with SID {}", ev.getSid());

        return ev;
    }

    @Override
    public synchronized ChannelEvent getEvent(ChannelID cId, EventID eId) throws ObjectNotFoundException {
        log.info("Getting Event {}/{}", cId, eId);
        return findEvent(cId, eId).orElseThrow(() -> new ObjectNotFoundException("Event", cId + "/" + eId));
    }

    @Override
    public ChannelEvent getEvent(long eSid) {
        return findEvent(eSid).orElseThrow(() -> new ObjectNotFoundException("Event", Long.toString(eSid)));
    }

    @Override
    public EventID getEventId(long eSid) {
        return getEvent(eSid).getId();
    }

    @Override
    public long getEventSid(ChannelID cId, EventID eId) throws ObjectNotFoundException {
        return 0;
    }

    @Override
    public List<ChannelEvent> getNext(long lastSid, long amount) {
        List<ChannelEvent> events = new ArrayList<>();
        while (events.size() < amount) {
            lastSid++;

            log.info("Checking for next event SID {}", lastSid);
            if (!chEvents.containsKey(lastSid)) {
                log.info("No such event, end of stream");
                return events;
            }

            log.info("Found next event SID {}, adding", lastSid);
            events.add(chEvents.get(lastSid));
            log.info("Incrementing SID");
        }

        log.info("Reached max amount of stream events, returning data");
        return events;
    }

    @Override
    public synchronized Optional<ChannelEvent> findEvent(ChannelID cId, EventID eId) {
        return Optional.ofNullable(evRefToSid.get(makeRef(cId, eId)))
                .flatMap(sid -> Optional.ofNullable(chEvents.get(sid)));
    }

    @Override
    public Optional<ChannelEvent> findEvent(long eSid) {
        return Optional.empty();
    }

    private List<Long> getOrComputeExts(long cSid) {
        return new ArrayList<>(chExtremities.computeIfAbsent(cSid, k -> new ArrayList<>()));
    }

    @Override
    public synchronized void updateExtremities(long cSid, List<Long> toRemove, List<Long> toAdd) {
        List<Long> exts = getOrComputeExts(cSid);
        exts.removeAll(toRemove);
        exts.addAll(toAdd);
        chExtremities.put(cSid, exts);
    }

    @Override
    public List<Long> getExtremities(long cSid) {
        return getOrComputeExts(cSid);
    }

    @Override
    public long insertIfNew(long cSid, ChannelState state) {
        if (Objects.nonNull(state.getSid())) {
            return state.getSid();
        }

        long sid = sSid.incrementAndGet();
        chStates.put(sid, new ChannelState(sid, state));
        return sid;
    }

    @Override
    public ChannelState getState(long stateSid) throws IllegalStateException {
        return Optional.ofNullable(chStates.get(stateSid)).orElseThrow(IllegalStateException::new);
    }

    @Override
    public void map(long evSid, long stateSid) {
        evStates.put(evSid, stateSid);
    }

    @Override
    public ChannelState getStateForEvent(long evSid) {
        Long sSid = evStates.get(evSid);
        if (Objects.isNull(sSid)) {
            throw new ObjectNotFoundException("State for Event SID", Long.toString(evSid));
        }

        ChannelState state = chStates.get(sSid);
        if (Objects.isNull(state)) {
            throw new ObjectNotFoundException("State SID", Long.toString(sSid));
        }

        return state;
    }

    @Override
    public boolean hasUser(String username) {
        return users.containsKey(username);
    }

    @Override
    public synchronized void storeUser(String username, String password) {
        if (hasUser(username)) {
            throw new IllegalStateException(username + " already exists");
        }

        users.put(username, password);
    }

    @Override
    public Optional<String> findPassword(String username) {
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public Optional<ChannelID> findChannelIdForAddress(String chAd) {
        return Optional.ofNullable(chAdrToId.get(chAd));
    }

    @Override
    public List<String> findChannelAddressForId(ChannelID id) {
        return new ArrayList<>(chIdToAdr.computeIfAbsent(id, k -> new HashSet<>()));
    }

    @Override
    public void map(ChannelID id, String chAd) {
        if (chAdrToId.containsKey(chAd)) {
            throw new AlreadyExistsException();
        }

        chAdrToId.put(chAd, id);
        chIdToAdr.computeIfAbsent(id, k -> new HashSet<>()).add(chAd);
    }

    @Override
    public void unmap(String chAd) {
        if (!chAdrToId.containsKey(chAd)) {
            throw new ObjectNotFoundException("Channel Address", chAd);
        }

        chIdToAdr.remove(chAdrToId.remove(chAd));
    }

}
