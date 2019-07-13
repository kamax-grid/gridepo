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

package io.kamax.grid.gridepo.core.identity.store.ldap;

import io.kamax.grid.gridepo.config.Identity.store.LdapConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;

import java.io.IOException;
import java.util.List;

public class LdapServer {

    public interface LdapConnFunction<LdapConnection, T> {

        T apply(LdapConnection master) throws CursorException, LdapException, IOException;

    }

    private LdapConfig.ConnServer endpoint;
    private LdapConfig.ConnBind bind;
    private List<String> baseDns;

    public LdapServer(LdapConfig.ConnServer endpoint, LdapConfig.ConnBind bind, List<String> baseDns) {
        this.endpoint = endpoint;
        this.bind = bind;
        this.baseDns = baseDns;
    }

    public String getLabel() {
        return endpoint.getHost() + ":" + endpoint.getPort();
    }

    protected synchronized LdapConnection getConn() {
        return new LdapNetworkConnection(endpoint.getHost(), endpoint.getPort(), endpoint.isTls());
    }

    protected synchronized LdapConnection getMasterConn() throws LdapException {
        LdapNetworkConnection conn = new LdapNetworkConnection(endpoint.getHost(), endpoint.getPort(), endpoint.isTls());
        bind(conn);
        return conn;
    }

    protected void bind(LdapConnection conn) throws LdapException {
        if (StringUtils.isNoneBlank(bind.getDn(), bind.getPassword())) {
            conn.bind(bind.getDn(), bind.getPassword());
        } else {
            conn.anonymousBind();
        }
    }

    public <T> T withConn(LdapConnFunction<LdapConnection, T> function) throws CursorException, LdapException, IOException {
        try (LdapConnection conn = getMasterConn()) {
            return function.apply(conn);
        }
    }

}
