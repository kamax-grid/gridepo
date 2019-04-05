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
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.BareCreateEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public abstract class StoreTest {

    private Store store;

    protected abstract Store getNewStore();

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
        String user = RandomStringUtils.random(12);
        assertFalse(store.hasUser(user));
        assertFalse(store.findPassword(user).isPresent());
    }

    @Test
    public void userSavedAndRead() {
        String user = RandomStringUtils.random(12);
        String password = RandomStringUtils.random(12);
        store.storeUser(user, password);

        assertTrue(store.hasUser(user));
        Optional<String> pwdStored = store.findPassword(user);
        assertTrue(pwdStored.isPresent());
        assertEquals(password, pwdStored.get());
    }

    @Test
    public void onlySavedUsersAreFound() {
        String user1 = RandomStringUtils.random(12);
        String user2 = RandomStringUtils.random(12);
        String user3 = RandomStringUtils.random(12);

        store.storeUser(user1, user1);
        store.storeUser(user2, user2);

        assertTrue(store.hasUser(user1));
        Optional<String> u1PwdStored = store.findPassword(user1);
        assertTrue(u1PwdStored.isPresent());
        assertEquals(user1, u1PwdStored.get());

        assertTrue(store.hasUser(user2));
        Optional<String> u2PwdStored = store.findPassword(user2);
        assertTrue(u2PwdStored.isPresent());
        assertEquals(user2, u2PwdStored.get());

        assertFalse(store.hasUser(user3));
        assertFalse(store.findPassword(user3).isPresent());
    }

    @Test
    public void saveAndGetChannel() {
        makeChannel();
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

        store.setAliases(ServerID.from("example.org"), cId, Collections.singleton(cAlias));
        Optional<ChannelID> addrAfter = store.lookupChannelAlias(cAlias);
        assertTrue(addrAfter.isPresent());
        assertEquals(cId, addrAfter.get());
    }

}
