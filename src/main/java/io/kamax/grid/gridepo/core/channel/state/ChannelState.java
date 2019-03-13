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

import io.kamax.grid.gridepo.core.UserID;
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

    private Long sid;
    private Map<String, ChannelEvent> data = new HashMap<>();

    private ChannelState() {
    }

    private ChannelState(List<ChannelEvent> events) {
        this(null, events);
    }

    public ChannelState(Long sid, List<ChannelEvent> events) {
        this.sid = sid;
        events.forEach(this::addEvent);
    }

    public ChannelState(Long sid, ChannelState state) {
        this.sid = sid;
        this.data = new HashMap<>(state.data);
    }

    private void addEvent(ChannelEvent ev) {
        String scope = GsonUtil.getStringOrThrow(ev.getData(), EventKey.Scope);
        String key = GsonUtil.getStringOrThrow(ev.getData(), EventKey.Type) + scope;
        data.put(key, ev);
    }

    public Long getSid() {
        return sid;
    }

    public Optional<ChannelEvent> find(String type, String scope) {
        return Optional.ofNullable(data.get(type + scope));
    }

    public <T> Optional<T> find(String type, Class<T> c) {
        return find(type, "", c);
    }

    public <T> Optional<T> find(String type, String scope, Class<T> c) {
        return find(type, scope).map(j -> GsonUtil.fromJson(j.getData(), c));
    }

    public <T> Optional<T> find(ChannelEventType type, Class<T> c) {
        return find(type.getId(), c);
    }

    public <T> Optional<T> find(ChannelEventType type, String scope, Class<T> c) {
        return find(type.getId(), scope, c);
    }

    public List<ChannelEvent> getEvents() {
        return new ArrayList<>(data.values());
    }

    public BareCreateEvent getCreation() {
        return find(ChannelEventType.Create, BareCreateEvent.class)
                .orElseThrow(IllegalStateException::new);
    }

    public String getCreationId() throws IllegalStateException {
        return getCreation().getId();
    }

    public String getCreator() {
        return getCreation().getContent().getCreator();
    }

    public Optional<BarePowerEvent.Content> getPowers() {
        return find(ChannelEventType.Power, BarePowerEvent.class)
                .map(BarePowerEvent::getContent);
    }

    public Optional<ChannelMembership> findMembership(String userId) {
        return find(ChannelEventType.Member, userId, BareMemberEvent.class)
                .map(ev -> ev.getContent().getAction())
                .flatMap(membershipMapper());
    }

    public ChannelMembership getMembership(UserID uId) {
        return getMembership(uId.full());
    }

    public ChannelMembership getMembership(String userId) {
        return findMembership(userId).orElse(ChannelMembership.Leave);
    }

    public Optional<ChannelJoinRule> getJoinRule() {
        return find(ChannelEventType.JoinRules, BareJoinRules.class)
                .map(ev -> ev.getContent().getRule())
                .flatMap(joinRuleMapper());
    }

    public ChannelState apply(ChannelEvent ev) {
        String scope = ev.getBare().getScope();
        if (Objects.isNull(scope)) {
            return this;
        }

        ChannelState state = new ChannelState();
        state.data = new HashMap<>(data);
        state.addEvent(ev);

        return state;
    }

}
