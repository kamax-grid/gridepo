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
        Type type = new TypeToken<Map<String, List<String>>>() {
        }.getType();
        return YamlConfigLoader.loadFromResource("/identity/store/ldap/defaultMappings.yml", type);
    }

}
