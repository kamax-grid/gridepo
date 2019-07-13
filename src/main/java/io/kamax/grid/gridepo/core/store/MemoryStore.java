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

import com.google.gson.JsonObject;
import io.kamax.grid.GenericThreePid;
import io.kamax.grid.ThreePid;
import io.kamax.grid.gridepo.config.IdentityConfig;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.auth.AuthPasswordDocument;
import io.kamax.grid.gridepo.core.auth.AuthResult;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.identity.AuthIdentityStore;
import io.kamax.grid.gridepo.core.identity.IdentityStore;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MemoryStore implements Store, IdentityStore {

    private static final Logger log = KxLog.make(MemoryStore.class);

    private static Map<String, MemoryStore> singleton = new ConcurrentHashMap<>();

    public static synchronized MemoryStore getNew() {
        return get(UUID.randomUUID().toString());
    }

    public static synchronized MemoryStore get(String o) {
        log.info("Getting memory store for namespace {}", o);
        return singleton.computeIfAbsent(o, k -> {
            log.info("Creating new memory store for namespace {}", k);
            return new MemoryStore();
        });
    }

    public static IdentityConfig.Store getMinimalConfig(String id) {
        IdentityConfig.Store cfg = new IdentityConfig.Store();
        cfg.setEnabled(true);
        cfg.setType("memory.internal");
        cfg.setConfig(GsonUtil.makeObj("connection", id));
        return cfg;
    }

    private AtomicLong eLid = new AtomicLong(0);
    private AtomicLong uLid = new AtomicLong(0);
    private AtomicLong chSid = new AtomicLong(0);
    private AtomicLong evLid = new AtomicLong(0);
    private AtomicLong evSid = new AtomicLong(0);
    private AtomicLong sSid = new AtomicLong(0);

    private Map<Long, EntityDao> entities = new ConcurrentHashMap<>();
    private Map<Long, UserDao> users = new ConcurrentHashMap<>();
    private Map<Long, List<String>> uTokens = new ConcurrentHashMap<>();
    private Map<Long, ChannelDao> channels = new ConcurrentHashMap<>();
    private Map<ChannelID, ChannelDao> chIdToDao = new ConcurrentHashMap<>();
    private Map<Long, ChannelEvent> chEvents = new ConcurrentHashMap<>();
    private Map<Long, ChannelState> chStates = new ConcurrentHashMap<>();
    private Map<Long, List<Long>> chFrontExtremities = new ConcurrentHashMap<>();
    private Map<Long, List<Long>> chBackExtremities = new ConcurrentHashMap<>();
    private Map<Long, Long> evStates = new ConcurrentHashMap<>();
    private Map<Long, List<ThreePid>> userThreepids = new ConcurrentHashMap<>();

    private Map<String, Long> uNameToLid = new ConcurrentHashMap<>();

    private Map<String, ChannelID> chAliasToId = new ConcurrentHashMap<>();
    private Map<ChannelID, Map<ServerID, Set<String>>> chIdToAlias = new ConcurrentHashMap<>();

    private Map<String, Long> evRefToLid = new ConcurrentHashMap<>();
    private Map<Long, Long> evSidToLid = new ConcurrentHashMap<>();
    private Map<Long, Long> evLidToSid = new ConcurrentHashMap<>();

    private MemoryStore() {
        // only via static
    }

    private String makeRef(ChannelEvent ev) {
        return makeRef(channels.get(ev.getChannelSid()).getId(), ev.getId());
    }

    private String makeRef(ChannelID cId, EventID eId) {
        return cId + "/" + eId;
    }

    @Override
    public long addEntity(String id, String type, boolean isLocal) {
        long lid = eLid.incrementAndGet();

        EntityDao dao = new EntityDao();
        dao.setLid(lid);
        dao.setId(id);
        dao.setType(type);
        dao.setLocal(isLocal);
        entities.put(lid, dao);
        return lid;
    }

    @Override
    public List<ChannelDao> listChannels() {
        return new ArrayList<>(channels.values());
    }

    @Override
    public Optional<ChannelDao> findChannel(long cSid) {
        return Optional.ofNullable(channels.get(cSid));
    }

    @Override
    public Optional<ChannelDao> findChannel(ChannelID cId) {
        return Optional.ofNullable(chIdToDao.get(cId));
    }

    @Override
    public long addToStream(long eLid) {
        long sid = evSid.incrementAndGet();
        evSidToLid.put(sid, eLid);
        evLidToSid.put(eLid, sid);
        log.debug("Added Event LID {} to stream with SID {}", eLid, sid);
        return sid;
    }

    @Override
    public long getStreamPosition() {
        return evSid.get();
    }

    @Override
    public ChannelDao saveChannel(ChannelDao ch) {
        long sid = chSid.incrementAndGet();
        ch = new ChannelDao(sid, ch.getId());
        channels.put(sid, ch);
        chIdToDao.put(ch.getId(), ch);
        return ch;
    }

    @Override
    public synchronized ChannelEvent saveEvent(ChannelEvent ev) {
        if (!ev.hasLid()) {
            ev.setLid(evLid.incrementAndGet());
        }

        chEvents.put(ev.getLid(), ev);
        evRefToLid.put(makeRef(ev), ev.getLid());

        log.info("Added new channel event with SID {}", ev.getLid());

        return ev;
    }

    @Override
    public synchronized ChannelEvent getEvent(ChannelID cId, EventID eId) throws ObjectNotFoundException {
        log.debug("Getting Event {}/{}", cId, eId);
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
    public long getEventTid(long cLid, EventID eId) {
        return getEvent(getChannel(cLid).getId(), eId).getSid();
    }

    @Override
    public Optional<Long> findEventLid(ChannelID cId, EventID eId) {
        return Optional.ofNullable(evRefToLid.get(makeRef(cId, eId)));
    }

    @Override
    public List<ChannelEvent> getNext(long lastSid, long amount) {
        List<ChannelEvent> events = new ArrayList<>();
        while (events.size() < amount) {
            lastSid++;

            log.info("Checking for next event SID {}", lastSid);
            if (!evSidToLid.containsKey(lastSid)) {
                log.info("No such event, end of stream");
                return events;
            }

            log.info("Found next event SID {}, adding", lastSid);
            events.add(getEvent(evSidToLid.get(lastSid)));
            log.info("Incrementing SID");
        }

        log.info("Reached max amount of stream events, returning data");
        return events;
    }

    @Override
    public List<ChannelEvent> getTimelineNext(long cLid, long lastTid, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        List<ChannelEvent> events = new ArrayList<>();
        while (events.size() < amount) {
            lastTid++;

            log.info("Checking for next event TID {}", lastTid);
            if (!evSidToLid.containsKey(lastTid)) {
                log.info("No such event, end of timeline");
                return events;
            }

            log.info("Found next event TID {}, adding", lastTid);
            events.add(getEvent(evSidToLid.get(lastTid)));
            log.info("Incrementing SID");
        }

        log.info("Reached max amount of stream events, returning data");

        return events;
    }

    @Override
    public List<ChannelEvent> getTimelinePrevious(long cLid, long lastTid, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        List<ChannelEvent> events = new ArrayList<>();
        while (events.size() < amount) {
            lastTid--;

            log.info("Checking for next event TID {}", lastTid);
            if (lastTid == 0 || !evSidToLid.containsKey(lastTid)) {
                log.info("No such event, end of timeline");
                return events;
            }

            log.info("Found next event TID {}, adding", lastTid);
            events.add(getEvent(evSidToLid.get(lastTid)));
            log.info("Incrementing SID");
        }

        log.info("Reached max amount of stream events, returning data");

        return events;
    }

    @Override
    public synchronized Optional<ChannelEvent> findEvent(ChannelID cId, EventID eId) {
        return Optional.ofNullable(evRefToLid.get(makeRef(cId, eId)))
                .flatMap(this::findEvent);
    }

    @Override
    public Optional<ChannelEvent> findEvent(long eSid) {
        return Optional.ofNullable(chEvents.get(eSid)).map(ev -> {
            ev.setSid(evLidToSid.get(ev.getLid()));
            return ev;
        });
    }

    private List<Long> getOrComputeBackwardExts(long cSid) {
        return new ArrayList<>(chBackExtremities.computeIfAbsent(cSid, k -> new ArrayList<>()));
    }

    @Override
    public void updateBackwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd) {
        List<Long> exts = getOrComputeBackwardExts(cLid);
        exts.removeAll(toRemove);
        exts.addAll(toAdd);
        chBackExtremities.put(cLid, exts);
    }

    @Override
    public List<Long> getBackwardExtremities(long cLid) {
        return getOrComputeBackwardExts(cLid);
    }

    private List<Long> getOrComputeForwardExts(long cLid) {
        return new ArrayList<>(chFrontExtremities.computeIfAbsent(cLid, k -> new ArrayList<>()));
    }

    @Override
    public synchronized void updateForwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd) {
        List<Long> exts = getOrComputeForwardExts(cLid);
        exts.removeAll(toRemove);
        exts.addAll(toAdd);
        chFrontExtremities.put(cLid, exts);
    }

    @Override
    public List<Long> getForwardExtremities(long cSid) {
        return getOrComputeForwardExts(cSid);
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
    public boolean hasUsername(String username) {
        return uNameToLid.containsKey(username);
    }

    @Override
    public long getUserCount() {
        return users.size();
    }

    @Override
    public long storeUser(long entityLid, String username, String password) {
        if (hasUsername(username)) {
            throw new IllegalStateException(username + " already exists");
        }

        long lid = uLid.incrementAndGet();
        UserDao dao = new UserDao();
        dao.setLid(lid);
        dao.setEntityLid(entityLid);
        dao.setUsername(username);
        dao.setPass(password);
        users.put(lid, dao);
        uNameToLid.put(username, lid);

        return lid;
    }

    @Override
    public Optional<UserDao> findUser(long lid) {
        return Optional.ofNullable(users.get(lid));
    }

    @Override
    public Optional<UserDao> findUser(String username) {
        return Optional.ofNullable(uNameToLid.get(username))
                .flatMap(lid -> Optional.ofNullable(users.get(lid)));
    }

    @Override
    public boolean hasUserAccessToken(String token) {
        for (List<String> tokens : uTokens.values()) {
            for (String v : tokens) {
                if (StringUtils.equals(v, token)) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<String> getTokens(long uLid) {
        return uTokens.computeIfAbsent(uLid, k -> new CopyOnWriteArrayList<>());
    }

    @Override
    public void insertUserAccessToken(long uLid, String token) {
        getTokens(uLid).add(token);
    }

    @Override
    public void deleteUserAccessToken(String token) {
        if (!hasUserAccessToken(token)) {
            throw new ObjectNotFoundException("Access token", "<REDACTED>");
        }

        uTokens.values().forEach(tokens -> tokens.remove(token));
    }

    private Map<ServerID, Set<String>> getChIdToAlias(ChannelID id) {
        return chIdToAlias.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
    }

    @Override
    public synchronized Optional<ChannelID> lookupChannelAlias(String chAlias) {
        return Optional.ofNullable(chAliasToId.get(chAlias));
    }

    @Override
    public synchronized Set<String> findChannelAlias(ServerID srvId, ChannelID id) {
        return getChIdToAlias(id).getOrDefault(srvId, new HashSet<>());
    }

    @Override
    public synchronized void setAliases(ServerID origin, ChannelID cId, Set<String> chAliases) {
        Map<ServerID, Set<String>> data = getChIdToAlias(cId);
        data.remove(origin);
        data.put(origin, new HashSet<>(chAliases));
        chAliases.forEach(cAlias -> chAliasToId.put(cAlias, cId));
    }

    @Override
    public synchronized void unmap(String chAd) {
        if (!chAliasToId.containsKey(chAd)) {
            throw new ObjectNotFoundException("Channel Address", chAd);
        }

        chIdToAlias.remove(chAliasToId.remove(chAd));
    }

    @Override
    public List<ThreePid> listThreePid(long userLid) {
        findUser(userLid).orElseThrow(() -> new ObjectNotFoundException("User LID " + userLid));

        return new ArrayList<>(userThreepids.computeIfAbsent(userLid, k -> new CopyOnWriteArrayList<>()));
    }

    @Override
    public List<ThreePid> listThreePid(long userLid, String medium) {
        return listThreePid(userLid).stream()
                .filter(v -> v.getMedium().equalsIgnoreCase(medium))
                .collect(Collectors.toList());
    }

    @Override
    public void addThreePid(long userLid, ThreePid tpid) {
        List<ThreePid> tpidList = listThreePid(userLid);
        tpidList.add(new GenericThreePid(tpid));
    }

    @Override
    public void removeThreePid(long userLid, ThreePid tpid) {
        GenericThreePid tpidIn = new GenericThreePid(tpid);
        List<ThreePid> tpidList = userThreepids.get(userLid);
        if (Objects.isNull(tpidList)) {
            throw new IllegalArgumentException("3PID not found");
        }

        if (!tpidList.remove(tpidIn)) {
            throw new IllegalArgumentException("3PID not found");
        }
    }

    @Override
    public Optional<AuthIdentityStore> forAuth() {
        return Optional.of(new AuthIdentityStore() {

            @Override
            public Set<String> getSupportedTypes() {
                return Collections.singleton("g.auth.password");
            }

            @Override
            public AuthResult authenticate(String type, JsonObject docJson) {
                AuthPasswordDocument doc = AuthPasswordDocument.from(docJson);
                if (!StringUtils.equals("g.auth.password", doc.getType())) {
                    throw new IllegalArgumentException();
                }


                if (StringUtils.equals("g.id.username", doc.getIdentifier().getType())) {
                    Optional<UserDao> dao = findUser(doc.getIdentifier().getValue());
                    if (!dao.isPresent()) {
                        return AuthResult.failed();
                    }

                    if (OpenBSDBCrypt.checkPassword(dao.get().getPass(), doc.getPassword().toCharArray())) {
                        return AuthResult.success(dao.get().getUsername());
                    } else {
                        return AuthResult.failed();
                    }
                }

                return AuthResult.failed();
            }

        });
    }

}
