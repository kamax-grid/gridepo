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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.event.BareGenericEvent;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEventType;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.util.GsonUtil;

public class ServerSession {

    private final Gridepo g;
    private final String srvId;

    public ServerSession(Gridepo g, String srvId) {
        this.g = g;
        this.srvId = srvId;
    }

    public JsonObject approveEvent(JsonObject obj) {
        BareGenericEvent evGen = GsonUtil.fromJson(obj, BareGenericEvent.class);
        if (!ChannelEventType.Member.match(evGen.getType())) {
            throw new ForbiddenException("Approving event with type " + evGen.getType());
        }

        BareMemberEvent mEv = GsonUtil.fromJson(obj, BareMemberEvent.class);
        if (!ChannelMembership.Invite.match(mEv.getContent().getAction())) {
            throw new ForbiddenException("Approving membership event with action " + mEv.getContent().getAction());
        }

        if (!g.isLocal(UserID.parse(mEv.getScope()))) {
            throw new ForbiddenException("Approving membership event for user " + mEv.getScope());
        }

        return g.getEventService().sign(obj);
    }

}
