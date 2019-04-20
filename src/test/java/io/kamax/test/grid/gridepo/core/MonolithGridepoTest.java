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

package io.kamax.test.grid.gridepo.core;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.MonolithGridepo;
import io.kamax.grid.gridepo.core.SyncData;
import io.kamax.grid.gridepo.core.SyncOptions;
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class MonolithGridepoTest {

    @Test
    public void basicRoomCreate() {
        GridepoConfig cfg = GridepoConfig.inMemory();
        cfg.setDomain("localhost");
        Gridepo g = new MonolithGridepo(cfg);
        g.getIdentity().register("gridepo", "gridepo");
        UserSession u = g.login("gridepo", "gridepo");
        String uId = u.getUser().getId().full();

        Channel ch = g.getChannelManager().createChannel(uId);
        assertEquals(ChannelMembership.Join, ch.getView().getState().getMembership(uId));

        SyncData data = u.sync(new SyncOptions().setToken("0").setTimeout(0));
        assertFalse(data.getEvents().isEmpty());
        assertTrue(StringUtils.isNotBlank(data.getPosition()));
        assertNotEquals("0", data.getPosition());
    }

}
