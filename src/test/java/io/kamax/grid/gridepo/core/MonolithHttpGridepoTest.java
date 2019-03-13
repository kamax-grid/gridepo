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

package io.kamax.grid.gridepo.core;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.federation.DataServerHttpClient;
import io.kamax.grid.gridepo.http.MonolithHttpGridepo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MonolithHttpGridepoTest {

    @Test
    public void inviteAndJoinOverFederation() {
        DataServerHttpClient.useHttps = false;

        GridepoConfig.ListenerNetwork net1 = new GridepoConfig.ListenerNetwork();
        net1.setProtocol("grid");
        net1.setType("server");
        GridepoConfig.Listener l1 = new GridepoConfig.Listener();
        l1.addNetwork(net1);
        l1.setPort(60001);
        GridepoConfig cfg1 = new GridepoConfig();
        cfg1.setDomain("localhost:" + l1.getPort());
        cfg1.getListeners().add(l1);
        cfg1.getCrypto().getSeed().put("jwt", "jwt");

        GridepoConfig.ListenerNetwork net2 = new GridepoConfig.ListenerNetwork();
        net2.setProtocol("grid");
        net2.setType("server");
        GridepoConfig.Listener l2 = new GridepoConfig.Listener();
        l2.addNetwork(net2);
        l2.setPort(60002);
        GridepoConfig cfg2 = new GridepoConfig();
        cfg2.setDomain("localhost:" + l2.getPort());
        cfg2.getListeners().add(l2);
        cfg2.getCrypto().getSeed().put("jwt", "jwt");

        MonolithHttpGridepo mg1 = new MonolithHttpGridepo(cfg1);
        MonolithHttpGridepo mg2 = new MonolithHttpGridepo(cfg2);

        Gridepo g1 = mg1.start();
        Gridepo g2 = mg2.start();

        UserSession u1g1 = g1.login("user1", "gridepo");
        UserSession u1g2 = g2.login("user1", "gridepo");
        String c1u1g1 = u1g1.createChannel().getId().full();
        u1g1.inviteToChannel(c1u1g1, new EntityAlias("grid", "@user1@" + cfg2.getDomain()));

        ChannelMembership mC1u1g2 = g1.getChannelManager().get(c1u1g1).getView().getState().getMembership(u1g2.getUser().getId().full());
        assertEquals(ChannelMembership.Invite, mC1u1g2);

        mg1.stop();
        mg2.stop();
    }

}
