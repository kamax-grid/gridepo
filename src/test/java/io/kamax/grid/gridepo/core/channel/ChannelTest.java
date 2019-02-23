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
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.algo.v0.ChannelAlgoV0_0;
import io.kamax.grid.gridepo.core.channel.event.BareCreateEvent;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.event.BarePowerEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.crypto.KeyManager;
import io.kamax.grid.gridepo.core.crypto.MemoryKeyStore;
import io.kamax.grid.gridepo.core.crypto.SignManager;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class ChannelTest {

    private final String domain = "localhost";
    private final String chanId = "!test";
    private final String janeId = "@jane";
    private final String johnId = "@john";

    private ChannelEventAuthorization assertAllowed(ChannelEventAuthorization auth) {
        assertTrue(auth.getReason(), auth.isValid());
        assertTrue(auth.getReason(), StringUtils.isBlank(auth.getReason()));
        assertTrue(auth.getReason(), auth.isAuthorized());

        return auth;
    }

    @Test
    public void basic() {
        SignalBus bus = new SignalBus();
        KeyManager keyMgr = new KeyManager(new MemoryKeyStore());
        SignManager signMgr = new SignManager(keyMgr);
        Store store = new MemoryStore();
        EventService evSvc = new EventService(domain, signMgr);
        ChannelAlgo algo = new ChannelAlgoV0_0();
        DataServerManager srvMgr = new DataServerManager();
        Channel c = new Channel(0, chanId, ServerID.from(domain), algo, evSvc, store, srvMgr, bus);
        assertNull(c.getView().getHead());
        assertNotNull(c.getView().getState());
        assertEquals(0, c.getView().getState().getServers().size());

        BareCreateEvent createEv = new BareCreateEvent();
        createEv.getContent().setCreator(janeId);
        createEv.setSender(janeId);
        JsonObject ev = evSvc.finalize(c.makeEvent(createEv));
        ChannelEventAuthorization auth = assertAllowed(c.injectLocal(ev));

        assertNotNull(c.getView().getHead());
        ChannelState state = c.getView().getState();
        assertNotNull(state);
        Long sid = state.getSid();
        assertEquals(1, state.getServers().size());
        assertEquals(auth.getEventId(), state.getCreationId());

        BarePowerEvent.Content afterCreatePls = state.getPowers().orElseGet(c::getDefaultPls);
        assertEquals(Long.MAX_VALUE, (long) afterCreatePls.getUsers().get(janeId));

        assertEquals(1, c.getView().getState().getServers().size());
        assertEquals(1, store.getExtremities(c.getId()).size());

        BareMemberEvent cJoinEv = new BareMemberEvent();
        cJoinEv.setSender(janeId);
        cJoinEv.setScope(janeId);
        cJoinEv.getContent().setAction(ChannelMembership.Join.getId());
        ev = evSvc.finalize(c.makeEvent(cJoinEv));

        auth = assertAllowed(c.injectLocal(ev));
        assertEquals(GsonUtil.getStringOrThrow(ev, EventKey.Id), auth.getEventId());
        state = c.getView().getState();
        assertNotEquals(sid, state.getSid());
        sid = state.getSid();
        assertEquals(1, state.getServers().size());
        assertEquals(ChannelMembership.Join, state.findMembership(janeId).orElse(ChannelMembership.Leave));

        BarePowerEvent cPlEv = new BarePowerEvent();
        cPlEv.setSender(janeId);
        cPlEv.getContent().getUsers().put(janeId, 100L);
        ev = evSvc.finalize(c.makeEvent(cPlEv));
        assertAllowed(c.injectLocal(ev));
        state = c.getView().getState();
        sid = state.getSid();
        assertEquals(1, state.getServers().size());
        BarePowerEvent.Content afterPlPls = state.getPowers().orElseGet(c::getDefaultPls);
        assertEquals(100, (long) afterPlPls.getUsers().get(janeId));

        BareMemberEvent invEv = new BareMemberEvent();
        invEv.setSender(janeId);
        invEv.setScope(johnId);
        invEv.getContent().setAction(ChannelMembership.Invite);
        ev = evSvc.finalize(c.makeEvent(invEv));
        assertAllowed(c.injectLocal(ev));
        state = c.getView().getState();
        assertEquals(1, state.getServers().size());
        Optional<ChannelMembership> johnMembership = state.findMembership(johnId);
        assertTrue(johnMembership.isPresent());
        assertEquals(ChannelMembership.Invite, johnMembership.get());

        BareMemberEvent joinEv = new BareMemberEvent();
        joinEv.setSender(johnId);
        joinEv.setScope(johnId);
        joinEv.getContent().setAction(ChannelMembership.Join);
        JsonObject madeEvent = c.makeEvent(joinEv);
        ev = evSvc.finalize(madeEvent);
        assertAllowed(c.injectLocal(ev));
        state = c.getView().getState();
        assertEquals(1, state.getServers().size());
        Optional<ChannelMembership> janeMembership = state.findMembership(janeId);
        johnMembership = state.findMembership(johnId);
        assertTrue(johnMembership.isPresent());
        assertTrue(janeMembership.isPresent());
        assertEquals(ChannelMembership.Join, janeMembership.get());
        assertEquals(ChannelMembership.Join, johnMembership.get());
    }

}
