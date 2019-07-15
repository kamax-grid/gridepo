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

package io.kamax.grid.gridepo.network.matrix.core.base;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.identity.ThreePid;
import io.kamax.grid.gridepo.core.identity.ThreePidLookup;
import io.kamax.grid.gridepo.core.identity.User;
import io.kamax.grid.gridepo.network.matrix.core.MatrixIdentityServer;

import java.time.Instant;
import java.util.Optional;

public class BaseMatrixIdentityServer implements MatrixIdentityServer {

    private final Gridepo g;

    public BaseMatrixIdentityServer(Gridepo g) {
        this.g = g;
    }

    @Override
    public ThreePidLookup lookup(ThreePid tpid) {
        ThreePidLookup lookup = new ThreePidLookup();
        lookup.setThreepid(tpid);
        lookup.setNotBefore(Instant.now().minusSeconds(60));
        lookup.setNotAfter(Instant.now().plusSeconds(5 * 60));

        Optional<User> user = g.getIdentity().findUser(tpid);
        if (!user.isPresent()) {
            return lookup;
        }


        return null;
    }

}
