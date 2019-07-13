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

package io.kamax.grid.gridepo;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.ServerSession;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.UserSession;
import io.kamax.grid.gridepo.core.auth.AuthService;
import io.kamax.grid.gridepo.core.auth.UIAuthSession;
import io.kamax.grid.gridepo.core.channel.ChannelDirectory;
import io.kamax.grid.gridepo.core.channel.ChannelManager;
import io.kamax.grid.gridepo.core.event.EventService;
import io.kamax.grid.gridepo.core.event.EventStreamer;
import io.kamax.grid.gridepo.core.federation.DataServerManager;
import io.kamax.grid.gridepo.core.federation.FederationPusher;
import io.kamax.grid.gridepo.core.identity.IdentityManager;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.store.Store;
import org.apache.commons.lang3.StringUtils;

public interface Gridepo {

    void start();

    void stop();

    boolean isStopping();

    GridepoConfig getConfig();

    String getDomain();

    ServerID getOrigin();

    default boolean isOrigin(String sId) {
        return StringUtils.equals(sId, getOrigin().full());
    }

    boolean isLocal(ServerID sId);

    SignalBus getBus();

    Store getStore();

    IdentityManager getIdentity();

    ChannelManager getChannelManager();

    ChannelDirectory getChannelDirectory();

    EventService getEventService();

    EventStreamer getStreamer();

    DataServerManager getServers();

    FederationPusher getFedPusher();

    AuthService getAuth();

    UIAuthSession login();

    UserSession login(String username, String password);

    UserSession login(JsonObject credentials);

    void logout(UserSession session);

    UserSession withToken(String token);

    boolean isLocal(UserID uId);

    ServerSession forServer(String srvId);

}
