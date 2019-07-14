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

package io.kamax.test.grid.gridepo.core.auth;

import io.kamax.grid.gridepo.core.auth.Credentials;
import io.kamax.grid.gridepo.core.auth.SecureCredentials;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;

public class SecureCredentialsTest {

    @Test
    public void basic() {
        String type = "g.test.auth.id.password";
        String pass = RandomStringUtils.randomAlphanumeric(6);
        Credentials creds = new Credentials(type, pass);

        SecureCredentials secCreds = SecureCredentials.from(creds);
        assertTrue(secCreds.matches(creds));
        assertTrue(secCreds.matches(pass));
        assertTrue(secCreds.matches(pass.toCharArray()));
    }

}
