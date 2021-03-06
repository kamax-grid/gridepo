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
import io.kamax.grid.gridepo.core.identity.GenericThreePid;
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
    protected static String a1;
    protected static UserID u1;
    protected static UserSession s1;

    protected static MonolithHttpGridepo mg2;
    protected static Gridepo g2;
    protected static String n2;
    protected static String a2;
    protected static UserID u2;
    protected static UserSession s2;

    @BeforeClass
    public static void init() {
        deinit();

        DataServerHttpClient.useHttps = false;

        String dh1 = "localhost";
        int dp1 = 60001;
        String dn1 = dh1 + ":" + dp1;
        GridepoConfig.Listener l1 = new GridepoConfig.Listener();
        l1.addNetwork(GridepoConfig.NetworkListeners.forGridDataServer());
        l1.addNetwork(GridepoConfig.NetworkListeners.forGridIdentityServer());
        l1.setPort(dp1);
        GridepoConfig cfg1 = GridepoConfig.inMemory();
        cfg1.setDomain(dn1);
        cfg1.getListeners().add(l1);

        String dh2 = "localhost";
        int dp2 = 60002;
        String dn2 = dh2 + ":" + dp2;
        GridepoConfig.Listener l2 = new GridepoConfig.Listener();
        l2.addNetwork(GridepoConfig.NetworkListeners.forGridDataServer());
        l2.addNetwork(GridepoConfig.NetworkListeners.forGridIdentityServer());
        l2.setPort(dp2);
        GridepoConfig cfg2 = GridepoConfig.inMemory();
        cfg2.setDomain(dn2);
        cfg2.getListeners().add(l2);

        MonolithHttpGridepo mg1 = new MonolithHttpGridepo(cfg1);
        MonolithHttpGridepo mg2 = new MonolithHttpGridepo(cfg2);

        n1 = "dark";
        a1 = "@" + n1 + "@" + dn1;
        g1 = mg1.start();
        g1.getFedPusher().setAsync(false);
        g1.register(n1, pass);
        s1 = g1.login(n1, pass);
        s1.getUser().addThreePid(new GenericThreePid("g.id.net.grid.alias", a1));
        u1 = s1.getUser().getGridId();

        n2 = "light";
        a2 = "@" + n2 + "@" + dn2;
        g2 = mg2.start();
        g2.getFedPusher().setAsync(false);
        g2.register(n2, pass);
        s2 = g2.login(n2, pass);
        s2.getUser().addThreePid(new GenericThreePid("g.id.net.grid.alias", a2));
        u2 = s2.getUser().getGridId();
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
