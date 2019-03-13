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
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public class ChannelDirectory {

    private static final Logger log = KxLog.make(ChannelDirectory.class);

    private final Store store;

    public ChannelDirectory(Store store) {
        this.store = store;
    }

    public Optional<ChannelID> lookup(String address) {
        return store.findChannelIdForAddress(address);
    }

    public List<String> getAddresses(ChannelID id) {
        return store.findChannelAddressForId(id);
    }

    public void map(String address, ChannelID id) {
        Optional<ChannelID> existingMap = lookup(address);
        if (existingMap.isPresent()) {
            ChannelID cId = existingMap.get();
            if (!cId.equals(id)) {
                throw new IllegalStateException("Channel address " + address + " is already mapped to " + cId.full());
            }

            log.info("Mapping {} -> {} already exists, ignoring call", address, id);
            return;
        }

        store.map(id, address);
    }

    public void unmap(String address) {
        Optional<ChannelID> id = lookup(address);
        if (!id.isPresent()) {
            throw new ObjectNotFoundException("Channel address", address);
        }

        store.unmap(address);
    }

}
