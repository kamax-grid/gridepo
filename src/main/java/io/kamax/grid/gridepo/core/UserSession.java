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
import io.kamax.grid.gridepo.core.channel.Channel;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.List;

public class UserSession {

    private Gridepo g;
    private User user;
    private String accessToken;

    public UserSession(Gridepo g, User user) {
        this.g = g;
        this.user = user;
    }

    public UserSession(User user, String accessToken) {
        this.user = user;
        this.accessToken = accessToken;
    }

    public User getUser() {
        return user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Channel createChannel() {
        return g.getChannelManager().createChannel(user.getUsername());
    }

    public SyncData sync(SyncOptions options) {
        Instant end = Instant.now().plusMillis(options.getTimeout());

        SyncData data = new SyncData();
        data.setPosition(options.getToken());

        if (StringUtils.isEmpty(options.getToken())) {
            options.setToken(Long.toString(0));
        }

        long sid = Long.parseLong(options.getToken());
        synchronized (g.getSyncLock()) {
            while (!g.isStopping() && Instant.now().isBefore(end)) {
                try {
                    g.getSyncLock().wait(1000L);
                } catch (InterruptedException e) {
                    // This is ok, we don't need to do anything
                }

                List<ChannelEvent> events = g.getStreamer().next(sid);
                if (!events.isEmpty()) {
                    data.setPosition(Long.toString(events.get(events.size() - 1).getSid()));
                    data.setEvents(events);
                    return data;
                }
            }
        }

        return data;
    }

}
