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
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerSession {

    private static final Logger log = LoggerFactory.getLogger(ServerSession.class);

    private final Gridepo g;
    private final String srvId;

    public ServerSession(Gridepo g, String srvId) {
        this.g = g;
        this.srvId = srvId;
    }

    public JsonObject approveInvite(JsonObject obj) {
        BareGenericEvent evGen = GsonUtil.fromJson(obj, BareGenericEvent.class);
        if (!ChannelEventType.Member.match(evGen.getType())) {
            throw new IllegalArgumentException("Approving event with type " + evGen.getType());
        }

        BareMemberEvent mEv = GsonUtil.fromJson(obj, BareMemberEvent.class);
        if (!ChannelMembership.Invite.match(mEv.getContent().getAction())) {
            throw new IllegalArgumentException("Approving membership event with action " + mEv.getContent().getAction());
        }

        if (!g.isLocal(UserID.parse(mEv.getScope()))) {
            throw new IllegalArgumentException("Approving membership event for user " + mEv.getScope());
        }

        return g.getEventService().sign(obj);
    }

    public List<ChannelEventAuthorization> push(List<JsonObject> events) {
        log.info("Got pushed {} event(s) from {}", events.size(), srvId);

        List<ChannelEventAuthorization> results = new ArrayList<>();
        Map<String, List<JsonObject>> evChanMap = new HashMap<>();
        for (JsonObject event : events) {
            String cId = GsonUtil.findString(event, EventKey.ChannelId).orElse("");
            if (StringUtils.isEmpty(cId)) {
                continue;
            }

            evChanMap.computeIfAbsent(cId, i -> new ArrayList<>()).add(event);
        }

        evChanMap.forEach((cId, objs) -> {
            log.info("Injecting {} event(s) in room {}", objs.size(), cId);
            results.addAll(g.getChannelManager().get(cId).injectRemote(srvId, objs));
        });

        return results;
    }

}
