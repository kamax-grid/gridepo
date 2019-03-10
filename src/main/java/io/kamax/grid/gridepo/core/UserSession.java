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
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.core.signal.AppStopping;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.util.KxLog;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class UserSession {

    private static final Logger log = KxLog.make(UserSession.class);

    private Gridepo g;
    private User user;
    private String accessToken;

    public UserSession(Gridepo g, User user) {
        this.g = g;
        this.user = user;
    }

    public UserSession(Gridepo g, User user, String accessToken) {
        this(g, user);
        this.accessToken = accessToken;
    }

    public UserSession(User user, String accessToken) {
        this.user = user;
        this.accessToken = accessToken;
    }

    @Handler
    private void signal(AppStopping signal) {
        log.info("Got {} signal, interrupting sync wait", signal.getClass().getSimpleName());
        synchronized (this) {
            notifyAll();
        }
    }

    @Handler
    private void signal(ChannelMessageProcessed signal) {
        log.info("Got {} signal, interrupting sync wait", signal.getClass().getSimpleName());
        synchronized (this) {
            notifyAll();
        }
    }

    public User getUser() {
        return user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Channel createChannel() {
        return g.getChannelManager().createChannel(user.getId().full());
    }

    public SyncData sync(SyncOptions options) {
        try {
            g.getBus().getMain().subscribe(this);
            g.getBus().forTopic(SignalTopic.Channel).subscribe(this);

            Instant end = Instant.now().plusMillis(options.getTimeout());

            SyncData data = new SyncData();
            data.setPosition(options.getToken());

            if (StringUtils.isEmpty(options.getToken())) {
                // Initial sync
                data.setPosition(Long.toString(0));
                return data;
            }

            long sid = Long.parseLong(options.getToken());
            while (Instant.now().isBefore(end)) {
                if (g.isStopping()) {
                    break;
                }

                List<ChannelEvent> events = g.getStreamer().next(sid);
                if (events.isEmpty()) {
                    try {
                        synchronized (this) {
                            wait(5000L);
                        }
                    } catch (InterruptedException e) {
                        // We loop back to check if we still need to sync
                        continue;
                    }

                    continue;
                }

                long position = events.stream()
                        .filter(ChannelEvent::isProcessed)
                        .max(Comparator.comparingLong(ChannelEvent::getSid))
                        .map(ChannelEvent::getSid)
                        .orElse(0L);
                data.setPosition(Long.toString(position));

                events = events.stream()
                        .filter(ev -> ev.isValid() && ev.isAllowed())
                        .filter(ev -> {
                            // FIXME move this into channel/state algo to check if a user can see an event in the stream

                            // If we are the author
                            if (StringUtils.equals(user.getId().full(), ev.getBare().getSender())) {
                                return true;
                            }

                            // if we are subscribed to the channel at that point in time
                            Channel c = g.getChannelManager().get(ev.getChannelId());
                            ChannelState state = c.getState(ev);
                            ChannelMembership m = state.getMembership(user.getId());
                            return m.isAny(ChannelMembership.Invite, ChannelMembership.Join);
                        })
                        .collect(Collectors.toList());

                data.setEvents(events);
                break;
            }

            return data;
        } finally {
            g.getBus().getMain().unsubscribe(this);
            g.getBus().forTopic(SignalTopic.Channel).unsubscribe(this);
        }
    }

    public String send(String cId, JsonObject data) {
        data.addProperty(EventKey.Sender, user.getId().full());
        return g.getChannelManager().get(cId).makeAndInject(data).getEventId();
    }

    public String inviteToChannel(String cId, EntityAlias uAl) {
        Channel c = g.getChannelManager().get(cId);
        String evId = c.invite(user.getId().full(), uAl).getId();
        return evId;
    }

    public String joinChannel(String cId) {
        BareMemberEvent ev = new BareMemberEvent();
        ev.setSender(user.getId().full());
        ev.setScope(user.getId().full());
        ev.getContent().setAction(ChannelMembership.Join);

        ChannelEventAuthorization r = g.getChannelManager().get(cId).makeAndInject(ev.getJson());
        if (!r.isAuthorized()) {
            throw new ForbiddenException(r.getReason());
        }

        return r.getEventId();
    }

    public String leaveChannel(String cId) {
        BareMemberEvent ev = new BareMemberEvent();
        ev.setSender(user.getId().full());
        ev.setScope(user.getId().full());
        ev.getContent().setAction(ChannelMembership.Leave);

        ChannelEventAuthorization r = g.getChannelManager().get(cId).makeAndInject(ev.getJson());
        if (!r.isAuthorized()) {
            throw new ForbiddenException(r.getReason());
        }

        return r.getEventId();
    }

}
