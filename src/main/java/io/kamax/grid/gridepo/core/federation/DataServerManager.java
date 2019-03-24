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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.kamax.grid.gridepo.core.ServerID;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DataServerManager {

    private LoadingCache<ServerID, DataServer> cache;

    public DataServerManager() {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(1000) // FIXME make it configurable
                .expireAfterAccess(3600, TimeUnit.SECONDS) // FIXME make it configurable
                .build(new CacheLoader<ServerID, DataServer>() {

                    @Override
                    public DataServer load(ServerID key) {
                        return new DataServer(key);
                    }

                });
    }

    public DataServer get(ServerID domain) {
        return cache.getUnchecked(domain);
    }

    public List<DataServer> get(Collection<ServerID> domains) {
        return get(domains, false);
    }

    public List<DataServer> get(Collection<ServerID> domains, boolean all) {
        // FIXME we should use
        return domains.stream()
                .map(d -> cache.getUnchecked(d))
                .filter(d -> all || d.isAvailable())
                .sorted(Comparator.comparingLong(DataServer::getLastCall).reversed())
                .collect(Collectors.toList());
    }

}
