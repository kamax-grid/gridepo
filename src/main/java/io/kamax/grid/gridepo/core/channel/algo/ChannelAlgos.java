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

package io.kamax.grid.gridepo.core.channel.algo;

import io.kamax.grid.gridepo.core.channel.algo.v0.ChannelAlgoV0_0;
import org.apache.commons.lang3.StringUtils;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

public class ChannelAlgos {

    public static String defaultVersion() {
        return ChannelAlgoV0_0.Version;
    }

    public static ChannelAlgo get(String channelVersion) throws NoSuchElementException {
        if (StringUtils.isEmpty(channelVersion)) {
            channelVersion = defaultVersion();
        }

        ServiceLoader<ChannelAlgoLoader> svcLoader = ServiceLoader.load(ChannelAlgoLoader.class);
        while (svcLoader.iterator().hasNext()) {
            Optional<ChannelAlgo> algo = svcLoader.iterator().next().apply(channelVersion);
            if (algo.isPresent()) {
                return algo.get();
            }
        }

        throw new NoSuchElementException();
    }

}
