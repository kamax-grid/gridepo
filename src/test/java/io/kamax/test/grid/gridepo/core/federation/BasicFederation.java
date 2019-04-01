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

package io.kamax.test.grid.gridepo.core.federation;

import io.kamax.grid.gridepo.core.*;
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.event.BareMessageEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BasicFederation extends Federation {

    @Test
    public void inviteAndJoin() {
        Channel g1c1 = s1.createChannel();
        String c1Id = g1c1.getId().full();
        s1.inviteToChannel(c1Id, new EntityGUID("grid", UserID.Sigill + n2 + EntityAlias.Delimiter + g2.getDomain())); // TODO use UserAlias sigill

        ChannelMembership g1u2c1 = g1c1.getView().getState().getMembership(u2);
        assertEquals(ChannelMembership.Invite, g1u2c1);

        Channel g2c1 = g2.getChannelManager().get(c1Id);
        ChannelMembership g2u2c1 = g2c1.getView().getState().getMembership(u2);
        assertEquals(ChannelMembership.Invite, g2u2c1);

        String joinEvId = s2.joinChannel(c1Id);
        assertEquals(joinEvId, g1c1.getView().getHead().full());
        assertEquals(joinEvId, g2c1.getView().getHead().full());

        g1u2c1 = g1c1.getView().getState().getMembership(u2);
        assertEquals(ChannelMembership.Join, g1u2c1);
        g2u2c1 = g2c1.getView().getState().getMembership(u2);
        assertEquals(ChannelMembership.Join, g2u2c1);
    }

    @Test
    public void joinPublicRoom() {
        String cId = makeSharedChannel();
        Channel g1c1 = g1.getChannelManager().get(cId);
        Channel g2c1 = g2.getChannelManager().get(cId);

        assertEquals(g1c1.getId(), g2c1.getId());
        assertEquals(2, g1c1.getView().getAllServers().size());
        assertEquals(2, g2c1.getView().getAllServers().size());
    }

    @Test
    public void sendMessage() {
        String cId = makeSharedChannel();
        Channel g1c1 = g1.getChannelManager().get(cId);
        Channel g2c1 = g2.getChannelManager().get(cId);

        String g1MsgEvId = s1.send(cId, BareMessageEvent.build(u1, "test from " + n1).getJson());
        assertEquals(g1MsgEvId, g1c1.getView().getHead().full());
        assertEquals(g1MsgEvId, g2c1.getView().getHead().full());

        String g2MsgEvId = s2.send(cId, BareMessageEvent.build(u2, "test from " + n2).getJson());
        assertEquals(g2MsgEvId, g1c1.getView().getHead().full());
        assertEquals(g2MsgEvId, g2c1.getView().getHead().full());
    }

    @Test
    public void backfill() {
        String cId = makeSharedChannel();
        Channel g1c1 = g1.getChannelManager().get(cId);
        Channel g2c1 = g2.getChannelManager().get(cId);

        String g1EvId = g1c1.getView().getHead().full();

        g2.getFedPusher().setEnabled(false);
        String g2Ev1Id = s2.send(cId, BareMessageEvent.build(u2, "Message 1 from " + n2).getJson());
        assertEquals(g1EvId, g1c1.getView().getHead().full());
        assertEquals(g2Ev1Id, g2c1.getView().getHead().full());

        g2.getFedPusher().setEnabled(true);
        String g2Ev2Id = s2.send(cId, BareMessageEvent.build(u2, "Message 2 from " + n2).getJson());
        assertEquals(g2Ev2Id, g1c1.getView().getHead().full());
        assertEquals(g2Ev2Id, g2c1.getView().getHead().full());

        Optional<ChannelEvent> g1Ev1 = g1.getStore().findEvent(ChannelID.from(cId), EventID.from(g2Ev1Id));
        assertTrue(g1Ev1.isPresent());
    }

}
