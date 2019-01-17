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
import io.kamax.grid.gridepo.core.channel.algo.v0.ChannelAlgoV0_0;
import io.kamax.grid.gridepo.core.channel.event.BareCreateEvent;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.crypto.KeyManager;
import io.kamax.grid.gridepo.core.crypto.MemoryKeyStore;
import io.kamax.grid.gridepo.core.crypto.SignManager;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.store.MemoryStore;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChannelTest {

    private final String domain = "localhost";
    private final String chanId = "!test";
    private final String userId = "@test";

    private ChannelEventAuthorization assertAllowed(ChannelEventAuthorization auth) {
        assertTrue(auth.getReason(), auth.isValid());
        assertTrue(auth.getReason(), StringUtils.isBlank(auth.getReason()));
        assertTrue(auth.getReason(), auth.isAuthorized());

        return auth;
    }

    @Test
    public void basic() {
        KeyManager keyMgr = new KeyManager(new MemoryKeyStore());
        SignManager signMgr = new SignManager(keyMgr);
        ChannelAlgo algo = new ChannelAlgoV0_0();
        Store store = new MemoryStore();
        EventService evSvc = new EventService(domain, signMgr);
        DataServerManager srvMgr = new DataServerManager();
        Channel c = new Channel(0, chanId, domain, algo, store, srvMgr);
        assertNull(c.getView().getHead());
        assertNotNull(c.getView().getState());

        BareCreateEvent createEv = new BareCreateEvent();
        createEv.getContent().setCreator(userId);
        createEv.setSender(userId);
        JsonObject ev = evSvc.finalize(c.makeEvent(createEv));
        ChannelEventAuthorization auth = assertAllowed(c.inject(ev));

        assertNotNull(c.getView().getHead());
        ChannelState state = c.getView().getState();
        assertNotNull(state);
        assertEquals(auth.getEventId(), state.getCreationId());

        assertEquals(1, c.getView().getState().getServers().size());
        assertEquals(1, store.getExtremities(c.getId()).size());

        BareMemberEvent cJoinEv = new BareMemberEvent();
        cJoinEv.setSender(userId);
        cJoinEv.setScope(userId);
        cJoinEv.getContent().setAction(ChannelMembership.Join.getId());
        ev = evSvc.finalize(c.makeEvent(cJoinEv));

        auth = assertAllowed(c.inject(ev));
        assertEquals(GsonUtil.getStringOrThrow(ev, EventKey.Id), auth.getEventId());
        state = c.getView().getState();
        assertEquals(ChannelMembership.Join, state.getMembership(userId).orElse(ChannelMembership.Leave));
    }

}
