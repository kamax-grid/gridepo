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

import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MemoryStore implements Store {

    private AtomicLong chSid = new AtomicLong(0);
    private AtomicLong evSid = new AtomicLong(0);
    private AtomicLong sSid = new AtomicLong(0);

    private Map<Long, Channel> channels = new ConcurrentHashMap<>();
    private Map<Long, ChannelEvent> chEvents = new ConcurrentHashMap<>();
    private Map<Long, ChannelState> chStates = new ConcurrentHashMap<>();
    private Map<String, List<String>> chExtremities = new ConcurrentHashMap<>();
    private Map<Long, Long> evStates = new ConcurrentHashMap<>();

    private Map<String, Long> evRefToSid = new ConcurrentHashMap<>();

    private String makeRef(ChannelEvent ev) {
        return makeRef(ev.getChannelId(), ev.getId());
    }

    private String makeRef(String chId, String evId) {
        return chId + "/" + evId;
    }

    @Override
    public boolean hasEvent(String chId, String evId) {
        return evRefToSid.containsKey(makeRef(chId, evId));
    }

    @Override
    public synchronized ChannelEvent saveEvent(ChannelEvent ev) {
        if (!ev.hasSid()) {
            ev.setSid(evSid.incrementAndGet());
        }

        chEvents.put(ev.getSid(), ev);
        evRefToSid.put(makeRef(ev), ev.getSid());

        return ev;
    }

    @Override
    public synchronized ChannelEvent getEvent(String channelId, String eventId) throws IllegalStateException {
        return findEvent(channelId, eventId).orElseThrow(IllegalStateException::new);
    }

    @Override
    public synchronized Optional<ChannelEvent> findEvent(String channelId, String eventId) {
        return Optional.ofNullable(evRefToSid.get(makeRef(channelId, eventId)))
                .flatMap(sid -> Optional.ofNullable(chEvents.get(sid)));
    }

    @Override
    public void setExtremities(String chId, List<String> extremities) {
        chExtremities.put(chId, new ArrayList<>(extremities));
    }

    @Override
    public List<String> getExtremities(String channelId) {
        return chExtremities.computeIfAbsent(channelId, i -> new ArrayList<>());
    }

    @Override
    public long insertIfNew(String chId, ChannelState state) {
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

}
