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

package io.kamax.grid.gridepo.core.channel.state;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.channel.ChannelJoinRule;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.event.*;
import io.kamax.grid.gridepo.core.event.EventKey;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.*;
import java.util.function.Function;

public class ChannelState {

    private static Function<String, Optional<ChannelMembership>> membershipMapper() {
        return id -> {
            for (ChannelMembership m : ChannelMembership.values()) {
                if (m.match(id)) {
                    return Optional.of(m);
                }
            }

            return Optional.empty();
        };
    }

    private static Function<String, Optional<ChannelJoinRule>> joinRuleMapper() {
        return id -> {
            for (ChannelJoinRule m : ChannelJoinRule.values()) {
                if (m.match(id)) {
                    return Optional.of(m);
                }
            }

            return Optional.empty();
        };
    }

    public static ChannelState empty() {
        return new ChannelState();
    }

    private long sid;
    private String head;
    private Map<String, JsonObject> events = new HashMap<>();
    private transient Set<String> servers = new HashSet<>();

    private ChannelState() {
    }

    private ChannelState(String head, List<JsonObject> events) {
        this(0, head, events);
    }

    public ChannelState(long sid, String head, List<JsonObject> events) {
        this.sid = sid;
        this.head = head;
        events.forEach(this::addEvent);
    }

    public ChannelState(long sid, ChannelState state) {
        this.sid = sid;
        this.head = state.head;
        this.events = new HashMap<>(state.events);
        this.servers = new HashSet<>(state.servers);
    }

    private void addEvent(JsonObject ev) {
        String key = GsonUtil.getStringOrThrow(ev, EventKey.Type) + GsonUtil.getStringOrThrow(ev, EventKey.Scope);
        events.put(key, ev);
        servers.add(GsonUtil.getStringOrThrow(ev, EventKey.Origin));
    }

    public long getSid() {
        return sid;
    }

    public String getHead() {
        return head;
    }

    public Optional<JsonObject> find(String type, String scope) {
        return Optional.ofNullable(events.get(type + scope));
    }

    public <T> Optional<T> find(String type, Class<T> c) {
        return find(type, "", c);
    }

    public <T> Optional<T> find(String type, String scope, Class<T> c) {
        return find(type, scope).map(j -> GsonUtil.fromJson(j, c));
    }

    public <T> Optional<T> find(ChannelEventType type, Class<T> c) {
        return find(type.getId(), c);
    }

    public <T> Optional<T> find(ChannelEventType type, String scope, Class<T> c) {
        return find(type.getId(), scope, c);
    }

    public String getCreationId() throws IllegalStateException {
        return find(ChannelEventType.Create, BareCreateEvent.class)
                .map(BareCreateEvent::getId)
                .orElseThrow(IllegalStateException::new);
    }

    public Set<String> getServers() {
        return new HashSet<>(servers);
    }

    public Optional<BarePowerEvent.Content> getPowers() {
        return find(ChannelEventType.Power, BarePowerEvent.class)
                .map(BarePowerEvent::getContent);
    }

    public Optional<ChannelMembership> getMembership(String userId) {
        return find(ChannelEventType.Member, userId, BareMemberEvent.class)
                .map(ev -> ev.getContent().getAction())
                .flatMap(membershipMapper());
    }

    public Optional<ChannelJoinRule> getJoinRule() {
        return find(ChannelEventType.JoinRules, BareJoinRules.class)
                .map(ev -> ev.getContent().getRule())
                .flatMap(joinRuleMapper());
    }

    public ChannelState apply(JsonObject ev) {
        Optional<String> scope = GsonUtil.findString(ev, EventKey.Scope);
        if (!scope.isPresent()) {
            return this;
        }

        ChannelState state = new ChannelState();
        state.sid = sid;
        state.head = GsonUtil.getStringOrThrow(ev, EventKey.Id);
        state.events = new HashMap<>(events);
        state.servers = new HashSet<>(servers);
        state.addEvent(ev);

        return state;
    }

}
