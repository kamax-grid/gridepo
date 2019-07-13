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

package io.kamax.grid.gridepo.config.Identity.store;

import com.google.gson.reflect.TypeToken;
import io.kamax.grid.gridepo.util.YamlConfigLoader;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class GenericLdapConfig extends LdapConfig {

    @Override
    protected Map<String, List<String>> getDefaultIdentityMappings() {
        String data = "g.id.username:\n" +
                "  - 'sAMAccountName'\n" +
                "g.id.net.matrix:\n" +
                "  - 'sAMAccountName'\n" +
                "g.id.net.email:\n" +
                "  - 'email'\n" +
                "  - 'mailPrimaryAddress'\n" +
                "  - 'mail'\n" +
                "  - 'otherMailbox'\n" +
                "g.id.net.phone.msisdn:\n" +
                "  - 'msisdn'\n" +
                "  - 'telephoneNumber'\n" +
                "  - 'mobile'\n" +
                "  - 'homePhone'\n" +
                "  - 'otherTelephone'\n" +
                "  - 'otherMobile'\n" +
                "  - 'otherHomePhone'";

        Type type = new TypeToken<Map<String, List<String>>>() {
        }.getType();
        // FIXME loading from resource doesn't work, but it works in PostgreSQL store - why?
        //return YamlConfigLoader.loadFromResource("/identity/store/ldap/defaultMappings.yml", type);

        return YamlConfigLoader.loadAs(data, type);
    }

}
