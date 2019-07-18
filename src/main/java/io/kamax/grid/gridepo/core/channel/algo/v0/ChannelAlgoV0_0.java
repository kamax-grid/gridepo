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

package io.kamax.grid.gridepo.core.channel.algo.v0;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.channel.ChannelJoinRule;
import io.kamax.grid.gridepo.core.channel.ChannelMembership;
import io.kamax.grid.gridepo.core.channel.algo.ChannelAlgo;
import io.kamax.grid.gridepo.core.channel.event.*;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChannelAlgoV0_0 implements ChannelAlgo {

    public static final String Version = "0.0";
    static final long minDepth = 0;

    private BareGenericEvent toProto(JsonObject ev) {
        return GsonUtil.fromJson(ev, BareGenericEvent.class);
    }

    public BarePowerEvent getDefaultPowersEvent(String creator) {
        BarePowerEvent ev = new DefaultPowerEvent();
        ev.getContent().getUsers().put(creator, Long.MAX_VALUE);
        return ev;
    }

    public BarePowerEvent.Content getDefaultPowers(String creator) {
        return getDefaultPowersEvent(creator).getContent();
    }

    private boolean canDoMembership(long senderPl, ChannelMembership m, BarePowerEvent.Content pls) {
        Long actionPl = null;

        if (ChannelMembership.Kick.equals(m)) {
            actionPl = pls.getMembership().getKick();
        }
        if (ChannelMembership.Ban.equals(m)) {
            actionPl = pls.getMembership().getBan();
        }
        if (ChannelMembership.Invite.equals(m)) {
            actionPl = pls.getMembership().getInvite();
        }

        if (Objects.isNull(actionPl)) {
            throw new IllegalArgumentException();
        }

        return senderPl >= actionPl;
    }

    private boolean canEvent(BarePowerEvent.Content pls, long senderPl, BareEvent ev) {
        Long defPl;

        if (StringUtils.isNotEmpty(ev.getScope())) {
            defPl = pls.getDef().getState();
        } else {
            defPl = pls.getDef().getEvent();
        }

        return senderPl >= pls.getEvents().getOrDefault(ev.getType(), defPl);
    }

    private boolean canSetTo(long oldPl, long newPl, long withPl) {
        if (oldPl == newPl) {
            return true;
        }

        if (oldPl > withPl) {
            return false;
        }

        return withPl >= newPl;
    }

    private boolean willFail(long withPl, long defaultPl, Map<String, Long> oldPls, Map<String, Long> newPls) {
        return !Stream.concat(oldPls.keySet().stream(), newPls.keySet()
                .stream()).collect(Collectors.toSet()).stream()
                .allMatch(type -> {
                    long oldPl = oldPls.getOrDefault(type, defaultPl);
                    long newPl = newPls.getOrDefault(type, defaultPl);
                    return withPl >= oldPl && withPl >= newPl;
                });
    }

    private boolean canReplace(String sender, long withPl, BarePowerEvent.Content pls, BarePowerEvent.Content newPls) {
        boolean basic = canSetTo(pls.getDef().getEvent(), newPls.getDef().getEvent(), withPl) &&
                canSetTo(pls.getDef().getState(), newPls.getDef().getState(), withPl) &&
                canSetTo(pls.getDef().getUser(), newPls.getDef().getUser(), withPl);
        if (!basic) {
            return false;
        }

        boolean membership = canSetTo(pls.getMembership().getBan(), newPls.getMembership().getBan(), withPl) &&
                canSetTo(pls.getMembership().getKick(), newPls.getMembership().getKick(), withPl) &&
                canSetTo(pls.getMembership().getInvite(), newPls.getMembership().getInvite(), withPl);
        if (!membership) {
            return false;
        }

        if (willFail(withPl, pls.getDef().getEvent(), pls.getEvents(), newPls.getEvents())) {
            return false;
        }

        if (willFail(withPl, pls.getDef().getUser(), pls.getUsers(), newPls.getUsers())) {
            return false;
        }

        return Stream.concat(pls.getUsers().keySet().stream(), newPls.getUsers().keySet().stream())
                .collect(Collectors.toSet()).stream()
                .allMatch(id -> {
                    if (StringUtils.equals(sender, id)) {
                        // We already know we are not giving a higher PL
                        // It is ok to give ourselves the same PL or lower
                        return true;
                    }

                    long oldTargetPl = pls.getUsers().getOrDefault(id, pls.getDef().getUser());
                    long newTargetPl = newPls.getUsers().getOrDefault(id, newPls.getDef().getUser());
                    if (oldTargetPl == newTargetPl) {
                        // The PL is not changing, so it's OK
                        return true;
                    }

                    // We already know we are not giving a higher PL than we can
                    // We check if we have more PL than the target
                    return withPl > oldTargetPl;
                });
    }

    @Override
    public String getVersion() {
        return Version;
    }

    @Override
    public long getBaseDepth() {
        return minDepth;
    }

    @Override
    public long getCreateDepth() {
        return getBaseDepth() + 1;
    }

    @Override
    public EventID generateEventId(String domain) {
        return EventID.from(UUID.randomUUID().toString().replace("-", "") + RandomStringUtils.randomAlphanumeric(4), domain);
    }

    @Override
    public String validate(JsonObject evRaw) {
        BareGenericEvent ev = toProto(evRaw);

        if (StringUtils.isEmpty(ev.getId())) {
            return "Invalid event, no ID";
        }

        if (StringUtils.isEmpty(ev.getVersion())) {
            return "Event " + ev.getId() + ": Invalid: Version is missing/empty";
        }

        if (StringUtils.isEmpty(ev.getType())) {
            return "Event " + ev.getId() + ": Invalid: Type is missing/empty";
        }

        if (Objects.isNull(ev.getTimestamp())) {
            return "Event " + ev.getId() + ": Invalid: Timestamp is missing";
        }

        if (StringUtils.isEmpty(ev.getOrigin())) {
            return "Event " + ev.getId() + ": Invalid: Origin is missing/empty";
        }

        if (StringUtils.isEmpty(ev.getSender())) {
            return "Event " + ev.getId() + ": Invalid: Sender is missing/empty";
        }

        if (Objects.isNull(ev.getPreviousEvents())) {
            return "Event " + ev.getId() + ": Invalid: Parents missing";
        }

        if (Objects.isNull(ev.getDepth())) {
            return "Event " + ev.getId() + ": Invalid: Depth is missing";
        }

        return "";
    }

    @Override
    public ChannelEventAuthorization authorize(ChannelState state, EventID evId, JsonObject evRaw) {
        BareGenericEvent ev = toProto(evRaw);

        if (Objects.isNull(evId)) {
            evId = EventID.parse(ev.getId());
        }

        ChannelEventAuthorization.Builder auth = new ChannelEventAuthorization.Builder(evId);
        String validation = validate(evRaw);
        if (StringUtils.isNotEmpty(validation)) {
            return auth.invalid(validation);
        }

        String evType = ev.getType();

        Optional<BareCreateEvent> cOpt = state.find(ChannelEventType.Create.getId(), BareCreateEvent.class);
        if (ChannelEventType.Create.match(evType)) {
            if (cOpt.isPresent()) {
                return auth.deny("Room is already created");
            }

            if (ev.getDepth() != getCreateDepth()) { // FIXME Do this into some earlier event check
                return auth.deny("Invalid event: Depth is not " + getCreateDepth());
            }

            if (!ev.getPreviousEvents().isEmpty()) { // FIXME Do this into some earlier event check
                return auth.deny("Invalid create event: has previous events");
            }

            return auth.allow();
        }

        if (!cOpt.isPresent()) {
            return auth.deny("Room does not exist as per state");
        }

        BareCreateEvent.Content cEv = cOpt.map(BareCreateEvent::getContent).get();

        BarePowerEvent.Content pls = DefaultPowerEvent.applyDefaults(state.getPowers().orElseGet(() -> getDefaultPowers(cEv.getCreator())));
        String sender = ev.getSender();
        ChannelMembership senderMs = state.findMembership(sender).orElse(ChannelMembership.Leave);
        long senderPl = pls.getUsers().getOrDefault(sender, pls.getDef().getUser());

        if (ChannelEventType.Member.match(evType)) {
            BareMemberEvent mEv = GsonUtil.fromJson(evRaw, BareMemberEvent.class);
            String membership = mEv.getContent().getAction();
            String target = mEv.getScope();
            ChannelMembership targetMs = state.findMembership(target).orElse(ChannelMembership.Leave);
            long targetPl = pls.getUsers().getOrDefault(target, pls.getDef().getUser());

            if (ChannelMembership.Join.match(membership)) {
                if (!StringUtils.equals(sender, target)) {
                    return auth.deny("Sender and target are different");
                }

                if (ev.getDepth() == (getCreateDepth() + 1)) { // Initial join
                    if (ev.getPreviousEvents().size() != 1) {
                        return auth.deny("Initial join event can only have one parent");
                    }

                    String eCreateId = state.getCreationId();
                    String pCreateId = ev.getPreviousEvents().get(0);

                    if (!StringUtils.equals(eCreateId, pCreateId)) {
                        return auth.deny("Initial join does not refer to create event as parent");
                    }

                    if (!StringUtils.equals(cEv.getCreator(), target)) {
                        return auth.deny("Initial join does not match channel creator");
                    }

                    return auth.allow();
                } else { // Regular joins
                    if (ChannelMembership.Join.equals(targetMs)) {
                        return auth.allow();
                    }

                    if (ChannelMembership.Invite.equals(targetMs)) {
                        return auth.allow();
                    }

                    ChannelJoinRule rule = state.getJoinRule().orElse(ChannelJoinRule.Private);
                    if (ChannelJoinRule.Public.equals(rule)) {
                        return auth.allow();
                    } else {
                        return auth.deny("Public join is not allowed");
                    }
                }
            } else if (ChannelMembership.Invite.match(membership)) {
                if (!ChannelMembership.Join.equals(senderMs)) {
                    return auth.deny("Invite sender is not joined to the channel");
                }

                if (ChannelMembership.Ban.equals(targetMs)) {
                    return auth.deny("Invite target is banned");
                }

                if (ChannelMembership.Join.equals(targetMs)) {
                    return auth.deny("Invite target is already joined");
                }

                if (!canDoMembership(senderPl, ChannelMembership.Invite, pls)) {
                    return auth.deny("Sender does not have the required Power Level to invite");
                }

                return auth.allow();
            } else if (ChannelMembership.Leave.match(membership)) {
                boolean isSame = StringUtils.equals(sender, target);
                if (isSame && senderMs.isAny(ChannelMembership.Join, ChannelMembership.Invite)) {
                    return auth.allow();
                }

                if (!senderMs.equals(ChannelMembership.Join)) {
                    return auth.deny("Sender cannot send in a room they are not joined");
                }

                if (ChannelMembership.Ban.equals(targetMs) && canDoMembership(senderPl, ChannelMembership.Ban, pls)) {
                    return auth.deny("Sender does not have the required Power Level to remove a ban");
                }

                if (canDoMembership(senderPl, ChannelMembership.Kick, pls)) {
                    return auth.deny("Sender does not have the required Power Level to kick");
                }

                if (senderPl <= targetPl) {
                    return auth.deny("Sender Power Level is not higher than the target Power Level");
                }

                return auth.allow();
            } else if (ChannelMembership.Ban.match(membership)) {
                if (!senderMs.equals(ChannelMembership.Join)) {
                    return auth.deny("Sender cannot send in a room they are not joined");
                }

                if (canDoMembership(senderPl, ChannelMembership.Ban, pls)) {
                    return auth.deny("Sender does not have the required Power Level to ban");
                }

                if (senderPl <= targetPl) {
                    return auth.deny("Sender Power Level is not higher than the target Power Level");
                }

                return auth.allow();
            } else {
                return auth.deny("Unknown membership " + membership);
            }
        }

        if (!senderMs.equals(ChannelMembership.Join)) {
            return auth.deny("Sender cannot send in a room they are not joined");
        }

        if (!canEvent(pls, senderPl, ev)) {
            return auth.deny("Sender does not have minimum PL for event type " + ev.getType());
        }

        if (ChannelEventType.Power.match(evType)) {
            BarePowerEvent.Content newPls = DefaultPowerEvent.applyDefaults(GsonUtil.fromJson(evRaw, DefaultPowerEvent.class).getContent());
            if (pls.getDef().getEvent() > senderPl || newPls.getDef().getEvent() > senderPl) {
                return auth.deny("Sender is missing minimum Power Level to change Power Level settings");
            }

            if (pls.getDef().getState() > senderPl || newPls.getDef().getState() > senderPl) {
                return auth.deny("Sender is missing minimum Power Level to change Power Level settings");
            }

            if (!canReplace(sender, senderPl, pls, newPls)) {
                return auth.deny("Sender is missing minimum Power Level to change Power Level settings");
            }

            return auth.allow();
        }

        return auth.allow();
    }

    @Override
    public List<BareEvent> getCreationEvents(String creator) {
        List<BareEvent> events = new ArrayList<>();
        BareCreateEvent createEv = new BareCreateEvent();
        createEv.getContent().setCreator(creator);
        createEv.setSender(creator);

        BareMemberEvent cJoinEv = new BareMemberEvent();
        cJoinEv.setSender(creator);
        cJoinEv.setScope(creator);
        cJoinEv.getContent().setAction(ChannelMembership.Join);

        BarePowerEvent cPlEv = getDefaultPowersEvent(creator);
        cPlEv.setSender(creator);

        events.add(createEv);
        events.add(cJoinEv);
        events.add(cPlEv);

        return events;
    }

}
