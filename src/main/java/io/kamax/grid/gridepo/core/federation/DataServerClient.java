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

package io.kamax.grid.gridepo.core.federation;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.ChannelAlias;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.channel.ChannelLookup;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.structure.InviteApprovalRequest;
import io.kamax.grid.gridepo.core.identity.ThreePid;

import java.util.List;
import java.util.Optional;

public interface DataServerClient {

    boolean ping(String as, String target);

    JsonObject push(String as, String target, List<ChannelEvent> events);

    JsonObject approveInvite(String as, String target, InviteApprovalRequest data);

    JsonObject approveJoin(String as, String target, BareMemberEvent ev);

    Optional<ChannelLookup> lookup(String as, String target, ChannelAlias alias);

    Optional<JsonObject> getEvent(String as, String target, ChannelID cId, EventID eId);

    //FIXME this needs to go under Identity client
    Optional<UserID> lookupUser(String as, String target, ThreePid tpid);

}
