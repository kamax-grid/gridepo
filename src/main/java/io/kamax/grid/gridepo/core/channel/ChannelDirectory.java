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

package io.kamax.grid.gridepo.core.channel;

import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.channel.event.BareAliasEvent;
import io.kamax.grid.gridepo.core.channel.event.BareGenericEvent;
import io.kamax.grid.gridepo.core.channel.event.ChannelEventType;
import io.kamax.grid.gridepo.core.signal.ChannelMessageProcessed;
import io.kamax.grid.gridepo.core.signal.SignalBus;
import io.kamax.grid.gridepo.core.signal.SignalTopic;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.GsonUtil;
import io.kamax.grid.gridepo.util.KxLog;
import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public class ChannelDirectory {

    private static final Logger log = KxLog.make(ChannelDirectory.class);

    private final ServerID origin;
    private final Store store;
    private final SignalBus bus;

    public ChannelDirectory(ServerID origin, Store store, SignalBus bus) {
        this.origin = origin;
        this.store = store;
        this.bus = bus;

        bus.forTopic(SignalTopic.Channel).subscribe(this);
    }

    @Handler
    private void handler(ChannelMessageProcessed evP) {
        if (!evP.getAuth().isAuthorized()) {
            return;
        }

        BareGenericEvent bEv = evP.getEvent().getBare();
        String type = bEv.getType();
        if (!ChannelEventType.Alias.match(type)) {
            return;
        }

        if (!origin.equals(ServerID.parse(bEv.getOrigin()))) {
            return;
        }

        BareAliasEvent ev = GsonUtil.fromJson(evP.getEvent().getData(), BareAliasEvent.class);
        map(ChannelID.from(ev.getChannelId()), ev.getContent().getAliases());
    }

    public Optional<ChannelID> lookup(String alias) {
        return store.lookupChannelAlias(alias);
    }

    public List<String> getAddresses(ChannelID id) {
        return store.findChannelAlias(id);
    }

    public void map(ChannelID id, List<String> aliases) {
        store.setAliases(origin, id, aliases);
    }

    public void unmap(String alias) {
        Optional<ChannelID> id = lookup(alias);
        if (!id.isPresent()) {
            throw new ObjectNotFoundException("Channel alias", alias);
        }

        store.unmap(alias);
    }

}
