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

package io.kamax.grid.gridepo.core.auth;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class SecureCredentials {

    public static SecureCredentials from(Credentials creds) {
        String salt = RandomStringUtils.randomAlphanumeric(16); // Salt requires 16 bytes
        String endData = OpenBSDBCrypt.generate(creds.getData(), salt.getBytes(StandardCharsets.UTF_8), 12);

        return new SecureCredentials(creds.getType(), endData);
    }

    private final String type;
    private final String data;

    public SecureCredentials(String type, String data) {
        this.type = Objects.requireNonNull(type);
        this.data = Objects.requireNonNull(data);
    }

    public String getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public boolean matches(char[] creds) {
        return OpenBSDBCrypt.checkPassword(getData(), creds);
    }

    public boolean matches(String creds) {
        return matches(creds.toCharArray());
    }

    public boolean matches(Credentials creds) {
        if (!StringUtils.equals(getType(), creds.getType())) {
            throw new IllegalArgumentException("Invalid credentials type: expected " + getType() + " but was given " + creds.getType());
        }

        return matches(creds.getData());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecureCredentials that = (SecureCredentials) o;
        return type.equals(that.type) &&
                data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, data);
    }

}
