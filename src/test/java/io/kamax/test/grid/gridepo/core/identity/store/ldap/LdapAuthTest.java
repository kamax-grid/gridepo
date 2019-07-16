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

package io.kamax.test.grid.gridepo.core.identity.store.ldap;

import com.google.gson.JsonObject;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import io.kamax.grid.gridepo.config.Identity.store.GenericLdapConfig;
import io.kamax.grid.gridepo.config.Identity.store.LdapConfig;
import io.kamax.grid.gridepo.core.GridType;
import io.kamax.grid.gridepo.core.auth.AuthResult;
import io.kamax.grid.gridepo.core.identity.store.ldap.LdapIdentityStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertFalse;

public class LdapAuthTest {

    private static InMemoryDirectoryServer ds;
    private static ArrayList<String> dnList = new ArrayList<>();

    private static String domain = "example.org";
    private static String host = "localhost";
    private static String mxisdCn = "cn=mxisd";
    private static String mxisdPw = "mxisd";
    private static String idType = "uid";
    private static String idAttribute = "saMAccountName";
    private static String userId = "john";
    private static String userPw = "doe";

    @BeforeClass
    public static void beforeClass() throws LDAPException {
        dnList.add("dc=1,dc=mxisd,dc=example,dc=org");
        dnList.add("dc=2,dc=mxisd,dc=example,dc=org");
        dnList.add("dc=3,dc=mxisd,dc=example,dc=org");

        InMemoryListenerConfig lCfg = InMemoryListenerConfig.createLDAPConfig(host, 65001);
        InMemoryDirectoryServerConfig config =
                new InMemoryDirectoryServerConfig(dnList.get(0), dnList.get(1), dnList.get(2));
        config.addAdditionalBindCredentials(mxisdCn, mxisdPw);
        config.setListenerConfigs(lCfg);

        ds = new InMemoryDirectoryServer(config);
        ds.startListening();
    }

    @AfterClass
    public static void afterClass() {
        ds.shutDown(true);
    }

    private LdapConfig buildCfg() {
        LdapConfig.ConnServer srv = new LdapConfig.ConnServer();
        srv.setHost(host);
        srv.setPort(65001);

        LdapConfig.Connection conn = new LdapConfig.Connection();
        conn.setServers(Collections.singletonList(srv));
        conn.getBind().setDn(mxisdCn);
        conn.getBind().setPassword(mxisdPw);

        LdapConfig cfg = new GenericLdapConfig();
        cfg.setBaseDNs(dnList);
        cfg.setConnection(conn);
        Map<String, List<String>> maps = cfg.getIdentity().getMapping();
        maps.put(GridType.id().local().username().getId(), Collections.singletonList(idAttribute));
        cfg.getIdentity().setMapping(maps);

        return cfg;
    }

    @Test
    public void notFound() {
        LdapIdentityStore p = new LdapIdentityStore(buildCfg());

        JsonObject id = new JsonObject();
        id.addProperty("type", GridType.id().local().username().getId());
        id.addProperty("value", userId);
        JsonObject doc = new JsonObject();
        doc.addProperty("type", "g.auth.id.password");
        doc.add("identifier", id);
        doc.addProperty("password", userPw);

        Optional<AuthResult> result = p.authenticate(doc);
        assertFalse(result.isPresent());
    }

}
