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

package io.kamax.test.grid.gridepo.core.channel;

import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.algo.v0.ChannelAlgoV0_0;
import io.kamax.grid.gridepo.core.channel.event.BareCreateEvent;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.event.BarePowerEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.crypto.Cryptopher;
import io.kamax.grid.gridepo.core.crypto.ed25519.Ed25519Cryptopher;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.core.store.crypto.MemoryKeyStore;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class ChannelTest {

    private final String domain = "localhost";
    private final ServerID sId = ServerID.from(domain);
    private final ChannelID cId = ChannelID.from("test", "domain");
    private final UserID janeId = UserID.from("jane", domain);
    private final UserID johnId = UserID.from("john", domain);

    private ChannelEventAuthorization assertAllowed(ChannelEventAuthorization auth) {
        assertTrue(auth.getReason(), auth.isValid());
        assertTrue(auth.getReason(), StringUtils.isBlank(auth.getReason()));
        assertTrue(auth.getReason(), auth.isAuthorized());

        return auth;
    }

    @Test
    public void basic() {
        SignalBus bus = SignalBus.getDefault();
        Cryptopher crypto = new Ed25519Cryptopher(new MemoryKeyStore());
        Store store = new MemoryStore();
        EventService evSvc = new EventService(sId, crypto);
        ChannelAlgo algo = new ChannelAlgoV0_0();
        DataServerManager srvMgr = new DataServerManager();
        ChannelDao cDao = store.saveChannel(new ChannelDao(cId));
        Channel c = new Channel(cDao, sId, algo, evSvc, store, srvMgr, bus);
        assertNull(c.getView().getHead());
        assertNotNull(c.getView().getState());
        assertEquals(0, c.getView().getAllServers().size());

        BareCreateEvent createEv = BareCreateEvent.withCreator(janeId);
        ChannelEventAuthorization auth = assertAllowed(c.offer(createEv));

        assertNotNull(c.getView().getHead());
        ChannelState state = c.getView().getState();
        assertNotNull(state);
        Long sid = state.getSid();
        assertEquals(auth.getEventId().full(), state.getCreationId());
        assertEquals(1, c.getExtremities().size());
        assertEquals(auth.getEventId(), c.getExtremityIds().get(0));

        BarePowerEvent.Content afterCreatePls = state.getPowers().orElseGet(c::getDefaultPls);
        assertEquals(Long.MAX_VALUE, (long) afterCreatePls.getUsers().get(janeId.full()));
        assertEquals(0, c.getView().getAllServers().size());
        assertEquals(1, store.getForwardExtremities(c.getSid()).size());

        BareMemberEvent cJoinEv = BareMemberEvent.joinAs(janeId);
        assertAllowed(c.offer(cJoinEv));
        state = c.getView().getState();
        assertNotEquals(sid, state.getSid());
        sid = state.getSid();
        assertEquals(1, c.getView().getAllServers().size());
        assertTrue(c.getView().isJoined(sId));
        assertEquals(0, c.getView().getOtherServers().size());
        Assert.assertEquals(ChannelMembership.Join, state.findMembership(janeId.full()).orElse(ChannelMembership.Leave));

        BarePowerEvent cPlEv = new BarePowerEvent();
        cPlEv.setSender(janeId);
        cPlEv.getContent().getUsers().put(janeId.full(), 100L);
        assertAllowed(c.offer(cPlEv));
        state = c.getView().getState();
        assertNotEquals(sid, state.getSid());
        sid = state.getSid();
        assertEquals(1, c.getView().getAllServers().size());
        BarePowerEvent.Content afterPlPls = state.getPowers().orElseGet(c::getDefaultPls);
        assertEquals(100, (long) afterPlPls.getUsers().get(janeId.full()));

        BareMemberEvent invEv = BareMemberEvent.inviteAs(johnId, janeId);
        assertAllowed(c.offer(invEv));
        state = c.getView().getState();
        assertNotEquals(sid, state.getSid());
        sid = state.getSid();
        assertEquals(1, c.getView().getAllServers().size());
        Optional<ChannelMembership> johnMembership = state.findMembership(johnId.full());
        assertTrue(johnMembership.isPresent());
        assertEquals(ChannelMembership.Invite, johnMembership.get());

        BareMemberEvent joinEv = BareMemberEvent.joinAs(johnId);
        assertAllowed(c.offer(joinEv));
        state = c.getView().getState();
        assertNotEquals(sid, state.getSid());
        assertEquals(1, c.getView().getAllServers().size());

        Optional<ChannelMembership> janeMembership = state.findMembership(janeId.full());
        johnMembership = state.findMembership(johnId.full());
        assertTrue(johnMembership.isPresent());
        assertTrue(janeMembership.isPresent());
        assertEquals(ChannelMembership.Join, janeMembership.get());
        assertEquals(ChannelMembership.Join, johnMembership.get());
    }

}
