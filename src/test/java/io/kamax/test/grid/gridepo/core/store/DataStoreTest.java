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

package io.kamax.test.grid.gridepo.core.store;

import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.auth.Credentials;
import io.kamax.grid.gridepo.core.auth.SecureCredentials;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.BareCreateEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.identity.GenericThreePid;
import io.kamax.grid.gridepo.core.identity.ThreePid;
import io.kamax.grid.gridepo.core.store.DataStore;
import io.kamax.grid.gridepo.core.store.UserDao;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public abstract class DataStoreTest {

    private DataStore store;

    protected abstract DataStore getNewStore();

    private long makeChannel() {
        ChannelID id = ChannelID.from(UUID.randomUUID().toString(), "example.org");
        ChannelDao daoBefore = new ChannelDao(id);
        assertEquals(id, daoBefore.getId());
        ChannelDao daoAfter = store.saveChannel(daoBefore);
        assertEquals(id, daoAfter.getId());
        assertNotEquals(daoBefore, daoAfter);
        assertNotEquals(daoBefore.getSid(), daoAfter.getSid());
        assertEquals(daoBefore.getId(), daoAfter.getId());
        return daoAfter.getSid();
    }

    @Before
    public void before() {
        store = getNewStore();
    }

    @Test
    public void newUserDoesNotExistInNewStore() {
        String user = RandomStringUtils.randomAlphanumeric(12);
        assertFalse(store.hasUsername(user));
        assertFalse(store.findUser(user).isPresent());
    }

    @Test
    public void userSavedAndRead() {
        String user = RandomStringUtils.randomAlphanumeric(12);
        long uLid = store.addUser(user);
        char[] password = RandomStringUtils.randomAlphanumeric(12).toCharArray();
        store.addCredentials(uLid, new Credentials("g.auth.id.password", password));

        assertTrue(store.hasUsername(user));
        Optional<UserDao> dao = store.findUser(user);
        assertTrue(dao.isPresent());
        SecureCredentials creds = store.getCredentials(uLid, "g.auth.id.password");
        assertTrue(creds.matches(password));
    }

    @Test
    public void linkUserToStoreId() {
        String user = RandomStringUtils.randomAlphanumeric(12);
        long uLid = store.addUser(user);
        ThreePid storeUid = new GenericThreePid("g.test.id.local.store", RandomStringUtils.randomAlphanumeric(12));
        store.linkUserToStore(uLid, storeUid);
        Optional<UserDao> dao = store.findUserByStoreLink(storeUid);
        assertTrue(dao.isPresent());
        assertEquals(dao.get().getLid(), uLid);
        assertEquals(dao.get().getId(), user);
    }

    @Test
    public void onlySavedUsersAreFound() {
        String user1 = RandomStringUtils.randomAlphanumeric(12);
        String user2 = RandomStringUtils.randomAlphanumeric(12);
        String user3 = RandomStringUtils.randomAlphanumeric(12);

        long uLid1 = store.addUser(user1);
        store.addCredentials(uLid1, new Credentials("g.auth.id.password", user1));
        long uLid2 = store.addUser(user2);
        store.addCredentials(uLid2, new Credentials("g.auth.id.password", user2));

        assertTrue(store.hasUsername(user1));
        Optional<UserDao> u1Dao = store.findUser(user1);
        assertTrue(u1Dao.isPresent());
        SecureCredentials user1SecCreds = store.getCredentials(uLid1, "g.auth.id.password");
        assertNotNull(user1SecCreds);

        assertTrue(store.hasUsername(user2));
        Optional<UserDao> u2Dao = store.findUser(user2);
        assertTrue(u2Dao.isPresent());
        SecureCredentials user2SecCreds = store.getCredentials(uLid2, "g.auth.id.password");
        assertNotNull(user2SecCreds);

        assertFalse(store.hasUsername(user3));
        assertFalse(store.findUser(user3).isPresent());
    }

    @Test
    public void saveAndGetChannel() {
        makeChannel();
    }

    @Test
    public void channelRead() {
        long cLid = makeChannel();

        Optional<ChannelDao> cDaoOpt = store.findChannel(cLid);
        assertTrue(cDaoOpt.isPresent());
        ChannelDao cDao = cDaoOpt.get();
        assertEquals(cLid, cDao.getSid());

        ChannelID cId = cDao.getId();

        List<ChannelDao> channels = store.listChannels();
        boolean foundByLid = false;
        boolean foundById = false;
        for (ChannelDao channel : channels) {
            if (channel.getSid() == cLid) {
                foundByLid = true;
            }

            if (channel.getId().equals(cId)) {
                foundById = true;
            }
        }

        assertTrue(foundByLid);
        assertTrue(foundById);
    }

    @Test
    public void saveAndReadChannelEvent() {
        long cSid = makeChannel();
        ChannelEvent ev1 = ChannelEvent.forNotFound(cSid, EventID.from("sarce1", "example.org"));
        ChannelEvent evStored = store.saveEvent(ev1);
        ChannelEvent evRead = store.getEvent(evStored.getLid());
        assertEquals(evStored.getLid(), evRead.getLid());

        BareCreateEvent bEv2 = new BareCreateEvent();
        bEv2.getContent().setCreator(UserID.from("john", "example.org"));
        ChannelEvent ev2 = ChannelEvent.from(cSid, EventID.from("sarce2", "example.org"), bEv2.getJson());
        assertNotNull(ev2.getData());
        ev2 = store.saveEvent(ev2);
        assertNotNull(ev2.getData());
    }

    @Test
    public void saveAndReadChannelState() {
        long cSid = makeChannel();
        ChannelEvent ev1 = ChannelEvent.from(cSid, EventID.from("sarcs1", "example.org"), GsonUtil.parseObj("{\"hello\":\"world\"}"));
        ChannelEvent ev2 = ChannelEvent.from(cSid, EventID.from("sarcs2", "example.org"), GsonUtil.parseObj("{\"type\":\"world\",\"scope\":\"test\"}"));
        ev1 = store.saveEvent(ev1);
        ev2 = store.saveEvent(ev2);
        ChannelState state = ChannelState.empty().apply(ev1).apply(ev2);
        long evAmountBefore = state.getEvents().size();
        Long sSid = store.insertIfNew(cSid, state);
        ChannelState stateRead = store.getState(sSid);
        long evAmountAfter = stateRead.getEvents().size();
        assertEquals(stateRead.getSid(), sSid);
        assertEquals(evAmountBefore, evAmountAfter);

        // TODO implement equals on ChannelEvent - Do we want to?
        //assertTrue(state.getEvents().containsAll(stateRead.getEvents()));
        //assertTrue(stateRead.getEvents().containsAll(state.getEvents()));
    }

    @Test
    public void channelEventState() {
        long cSid = makeChannel();
        BareCreateEvent bEv = new BareCreateEvent();
        bEv.getContent().setCreator(UserID.from("john", "example.org"));
        ChannelEvent ev = ChannelEvent.from(cSid, EventID.from("sarce2", "example.org"), bEv.getJson());
        assertNotNull(ev.getData());
        ev = store.saveEvent(ev);

        ChannelState state = store.getState(store.insertIfNew(cSid, ChannelState.empty().apply(ev)));
        store.map(ev.getLid(), state.getSid());
        ChannelState stateRead = store.getStateForEvent(ev.getLid());
        assertEquals(state.getSid(), stateRead.getSid());
    }

    @Test
    public void channelAddressing() {
        String localpart = UUID.randomUUID().toString().replace("-", "");
        ChannelID cId = ChannelID.from(localpart, "example.org");
        String cAlias = "#" + localpart + "@example.org";

        Optional<ChannelID> addrBefore = store.lookupChannelAlias(cAlias);
        assertFalse(addrBefore.isPresent());

        store.setAliases(ServerID.fromDns("example.org"), cId, Collections.singleton(cAlias));
        Optional<ChannelID> addrAfter = store.lookupChannelAlias(cAlias);
        assertTrue(addrAfter.isPresent());
        assertEquals(cId, addrAfter.get());
    }

    @Test
    public void channelForwardExtremitiesReadAndWrite() {
        List<Long> data = Arrays.asList(1L, 2L, 3L);
        long cLid = makeChannel();
        List<Long> extremities = store.getForwardExtremities(cLid);
        assertTrue(extremities.isEmpty());

        store.updateForwardExtremities(cLid, extremities, data);
        extremities = store.getForwardExtremities(cLid);
        assertEquals(3, extremities.size());
        assertTrue(extremities.containsAll(data));

        store.updateForwardExtremities(cLid, data, Collections.emptyList());
        extremities = store.getForwardExtremities(cLid);
        assertTrue(extremities.isEmpty());
    }

    @Test
    public void channelBackwardExtremitiesReadAndWrite() {
        List<Long> data = Arrays.asList(1L, 2L, 3L);
        long cLid = makeChannel();
        List<Long> extremities = store.getBackwardExtremities(cLid);
        assertTrue(extremities.isEmpty());

        store.updateBackwardExtremities(cLid, extremities, data);
        extremities = store.getBackwardExtremities(cLid);
        assertEquals(3, extremities.size());
        assertTrue(extremities.containsAll(data));

        store.updateBackwardExtremities(cLid, data, Collections.emptyList());
        extremities = store.getBackwardExtremities(cLid);
        assertTrue(extremities.isEmpty());
    }

}
