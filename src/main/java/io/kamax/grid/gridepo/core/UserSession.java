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
import io.kamax.grid.gridepo.core.channel.*;
import io.kamax.grid.gridepo.core.channel.event.BareAliasEvent;
import io.kamax.grid.gridepo.core.channel.event.BareMemberEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.core.identity.User;
import io.kamax.grid.gridepo.core.signal.AppStopping;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.KxLog;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        return g.getChannelManager().createChannel(user.getGridId().full());
    }

    // FIXME evaluate if we should compute the exact state at the stream position in an atomic way
    public SyncData syncInitial() {
        SyncData data = new SyncData();
        data.setInitial(true);
        data.setPosition(Long.toString(g.getStreamer().getPosition()));

        // FIXME this doesn't scale - we only care about channels where the user has ever been into
        // so we shouldn't even deal with those. Need to make storage smarter in this case
        // or use a caching mechanism to know what's the membership status of a given user
        g.getChannelManager().list().forEach(cId -> {
            // FIXME we need to get the HEAD event of the timeline instead
            Channel c = g.getChannelManager().get(cId);
            EventID evID = c.getView().getHead();
            data.getEvents().add(g.getStore().getEvent(cId, evID));
        });

        return data;
    }

    public SyncData sync(SyncOptions options) {
        try {
            g.getBus().getMain().subscribe(this);
            g.getBus().forTopic(SignalTopic.Channel).subscribe(this);

            Instant end = Instant.now().plusMillis(options.getTimeout());

            SyncData data = new SyncData();
            data.setPosition(options.getToken());

            if (StringUtils.isEmpty(options.getToken())) {
                return syncInitial();
            }

            long sid = Long.parseLong(options.getToken());
            do {
                if (g.isStopping()) {
                    break;
                }

                List<ChannelEvent> events = g.getStreamer().next(sid);
                if (!events.isEmpty()) {
                    long position = events.stream()
                            .filter(ev -> ev.getMeta().isProcessed())
                            .max(Comparator.comparingLong(ChannelEvent::getSid))
                            .map(ChannelEvent::getSid)
                            .orElse(0L);
                    log.debug("Position after sync loop: {}", position);
                    data.setPosition(Long.toString(position));

                    events = events.stream()
                            .filter(ev -> ev.getMeta().isValid() && ev.getMeta().isAllowed())
                            .filter(ev -> {
                                // FIXME move this into channel/state algo to check if a user can see an event in the stream

                                // If we are the author
                                if (StringUtils.equalsAny(user.getGridId().full(), ev.getBare().getSender(), ev.getBare().getScope())) {
                                    return true;
                                }

                                // if we are subscribed to the channel at that point in time
                                Channel c = g.getChannelManager().get(ev.getChannelId());
                                ChannelState state = c.getState(ev);
                                ChannelMembership m = state.getMembership(user.getId());
                                log.info("Membership for Event LID {}: {}", ev.getLid(), m);
                                return m.isAny(ChannelMembership.Join);
                            })
                            .collect(Collectors.toList());

                    data.getEvents().addAll(events);
                }

                long waitTime = Math.min(options.getTimeout(), 1000L);
                if (waitTime > 0) {
                    try {
                        synchronized (this) {
                            wait(waitTime);
                        }
                    } catch (InterruptedException e) {
                        // We don't care. We log it in case of, but we'll just loop again
                        log.debug("Got interrupted while waiting on sync");
                    }
                }
            } while (end.isAfter(Instant.now()));

            return data;
        } finally {
            g.getBus().getMain().unsubscribe(this);
            g.getBus().forTopic(SignalTopic.Channel).unsubscribe(this);
        }
    }

    public String send(String cId, JsonObject data) {
        data.addProperty(EventKey.Sender, user.getGridId().full());
        return g.getChannelManager().get(cId).makeAndOffer(data).getEventId().full();
    }

    public String inviteToChannel(String cId, EntityGUID uAl) {
        Channel c = g.getChannelManager().get(cId);
        String evId = c.invite(user.getGridId().full(), uAl).getId().full();
        return evId;
    }

    public String joinChannel(String cId) {
        BareMemberEvent ev = new BareMemberEvent();
        ev.setSender(user.getGridId().full());
        ev.setScope(user.getGridId().full());
        ev.getContent().setAction(ChannelMembership.Join);

        ChannelEventAuthorization r = g.getChannelManager().get(cId).makeAndOffer(ev.getJson());
        if (!r.isAuthorized()) {
            throw new ForbiddenException(r.getReason());
        }

        return r.getEventId().full();
    }

    public Channel joinChannel(ChannelAlias cAlias) {
        return g.getChannelManager().join(cAlias, getUser().getGridId());
    }

    public String leaveChannel(String cId) {
        BareMemberEvent ev = new BareMemberEvent();
        ev.setSender(user.getGridId().full());
        ev.setScope(user.getGridId().full());
        ev.getContent().setAction(ChannelMembership.Leave);

        ChannelEventAuthorization r = g.getChannelManager().get(cId).makeAndOffer(ev.getJson());
        if (!r.isAuthorized()) {
            throw new ForbiddenException(r.getReason());
        }

        return r.getEventId().full();
    }

    public void addChannelAlias(String alias, ChannelID id) {
        Set<String> aliases = g.getChannelDirectory().getAliases(id);
        if (aliases.contains(alias)) {
            return;
        }

        aliases.add(alias);

        BareAliasEvent ev = new BareAliasEvent();
        ev.setScope(g.getOrigin().full());
        ev.setSender(user.getGridId().full());
        ev.getContent().setAliases(aliases);

        g.getChannelManager().get(id).makeAndOffer(ev.getJson());
    }

    public void removeChannelAlias(String alias) {
        ChannelID cId = g.getChannelDirectory().lookup(ChannelAlias.parse(alias), false)
                .map(ChannelLookup::getId).orElseThrow(() -> new ObjectNotFoundException("Channel Alias", alias));

        Set<String> aliases = g.getChannelDirectory().getAliases(cId);
        if (!aliases.contains(alias)) {
            return;
        }

        aliases.remove(alias);

        BareAliasEvent ev = new BareAliasEvent();
        ev.setScope(g.getOrigin().full());
        ev.setSender(user.getGridId().full());
        ev.getContent().setAliases(aliases);

        g.getChannelManager().get(cId).makeAndOffer(ev.getJson());
    }

    public Optional<ChannelLookup> lookup(ChannelAlias alias) {
        return g.getChannelDirectory().lookup(alias, true);
    }

    public TimelineChunk paginateTimeline(ChannelID cId, EventID anchor, TimelineDirection direction, long amount) {
        Channel c = g.getChannelManager().get(cId);
        if (TimelineDirection.Forward.equals(direction)) {
            return c.getTimeline().getNext(anchor, amount);
        } else {
            return c.getTimeline().getPrevious(anchor, amount);
        }
    }

}
