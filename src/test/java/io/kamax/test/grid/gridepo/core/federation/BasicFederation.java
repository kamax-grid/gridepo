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

import io.kamax.grid.gridepo.core.ChannelAlias;
import io.kamax.grid.gridepo.core.EntityAlias;
import io.kamax.grid.gridepo.core.EntityGUID;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.event.BareAliasEvent;
import io.kamax.grid.gridepo.core.channel.event.BareJoiningEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BasicFederation extends Federation {

    @Test
    public void inviteAndJoin() {
        String c1 = s1.createChannel().getId().full();
        s1.inviteToChannel(c1, new EntityGUID("grid", UserID.Sigill + n2 + EntityAlias.Delimiter + g2.getDomain())); // TODO use UserAlias sigill

        ChannelMembership g1u2c1 = g1.getChannelManager().get(c1).getView().getState().getMembership(u2);
        assertEquals(ChannelMembership.Invite, g1u2c1);

        ChannelMembership g2u2c1 = g2.getChannelManager().get(c1).getView().getState().getMembership(u2);
        assertEquals(ChannelMembership.Invite, g2u2c1);
    }

    @Test
    public void joinPublicRoom() {
        Channel g1c1 = s1.createChannel();
        String c1Id = g1c1.getId().full();
        ChannelAlias c1Alias = new ChannelAlias("test", g1.getDomain());

        BareAliasEvent aEv = new BareAliasEvent();
        aEv.addAlias(c1Alias);
        s1.send(c1Id, aEv.getJson());

        BareJoiningEvent joinRules = new BareJoiningEvent();
        joinRules.getContent().setRule("public");
        s1.send(c1Id, joinRules.getJson());

        Channel g2c1 = s2.joinChannel(c1Alias);

        assertEquals(g1c1.getId(), g2c1.getId());
    }

}
