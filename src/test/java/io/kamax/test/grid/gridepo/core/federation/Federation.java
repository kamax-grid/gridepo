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

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.ChannelAlias;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.event.BareAliasEvent;
import io.kamax.grid.gridepo.core.channel.event.BareJoiningEvent;
import io.kamax.grid.gridepo.core.federation.DataServerHttpClient;
import io.kamax.grid.gridepo.http.MonolithHttpGridepo;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class Federation {

    protected static String pass = "gridepo";

    protected static MonolithHttpGridepo mg1;
    protected static Gridepo g1;
    protected static String n1;
    protected static UserID u1;
    protected static UserSession s1;

    protected static MonolithHttpGridepo mg2;
    protected static Gridepo g2;
    protected static String n2;
    protected static UserID u2;
    protected static UserSession s2;

    @BeforeClass
    public static void init() {
        deinit();

        DataServerHttpClient.useHttps = false;

        GridepoConfig.ListenerNetwork net1 = new GridepoConfig.ListenerNetwork();
        net1.setProtocol("grid");
        net1.setApi("data");
        net1.setRole("server");
        GridepoConfig.Listener l1 = new GridepoConfig.Listener();
        l1.addNetwork(net1);
        l1.setPort(60001);
        GridepoConfig cfg1 = GridepoConfig.inMemory();
        cfg1.setDomain("localhost:" + l1.getPort());
        cfg1.getListeners().add(l1);

        GridepoConfig.ListenerNetwork net2 = new GridepoConfig.ListenerNetwork();
        net2.setProtocol("grid");
        net2.setApi("data");
        net2.setRole("server");
        GridepoConfig.Listener l2 = new GridepoConfig.Listener();
        l2.addNetwork(net2);
        l2.setPort(60002);
        GridepoConfig cfg2 = GridepoConfig.inMemory();
        cfg2.setDomain("localhost:" + l2.getPort());
        cfg2.getListeners().add(l2);

        MonolithHttpGridepo mg1 = new MonolithHttpGridepo(cfg1);
        MonolithHttpGridepo mg2 = new MonolithHttpGridepo(cfg2);

        g1 = mg1.start();
        g1.getFedPusher().setAsync(false);
        g2 = mg2.start();
        g2.getFedPusher().setAsync(false);

        n1 = "dark";
        n2 = "light";

        g1.getIdentity().register(n1, pass);
        g2.getIdentity().register(n2, pass);

        s1 = g1.login(n1, pass);
        u1 = s1.getUser().getId();
        s2 = g2.login(n2, pass);
        u2 = s2.getUser().getId();
    }

    @AfterClass
    public static void deinit() {
        if (Objects.nonNull(mg1)) {
            mg1.stop();
        }

        if (Objects.nonNull(mg2)) {
            mg2.stop();
        }
    }

    protected String makeSharedChannel() {
        Channel g1c1 = s1.createChannel();
        String cId = g1c1.getId().full();
        ChannelAlias c1Alias = new ChannelAlias("test", g1.getDomain());

        BareAliasEvent aEv = new BareAliasEvent();
        aEv.addAlias(c1Alias);
        s1.send(cId, aEv.getJson());

        BareJoiningEvent joinRules = new BareJoiningEvent();
        joinRules.getContent().setRule("public");
        s1.send(cId, joinRules.getJson());

        Channel g2c1 = s2.joinChannel(c1Alias);

        assertEquals(g1c1.getId(), g2c1.getId());
        assertEquals(2, g1c1.getView().getAllServers().size());
        assertEquals(2, g2c1.getView().getAllServers().size());

        return cId;
    }

}
