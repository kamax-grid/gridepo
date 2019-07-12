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

package io.kamax.grid.gridepo.core.identity;

import io.kamax.grid.ThreePid;
import io.kamax.grid.gridepo.config.IdentityConfig;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.core.store.UserDao;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public class IdentityManager {

    private IdentityConfig cfg;
    private Store store;

    public IdentityManager(IdentityConfig cfg, Store store) {
        this.cfg = cfg;
        this.store = store;
    }

    public boolean canRegister() {
        return store.getUserCount() == 0 || cfg.getRegister().isEnabled();
    }

    public boolean isUsernameAvailable(String username) {
        return !store.hasUsername(username);
    }

    public synchronized void register(String username, String password) {
        username = Objects.requireNonNull(username).toLowerCase();
        password = Objects.requireNonNull(password);

        if (store.hasUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        String salt = RandomStringUtils.randomAlphanumeric(16);
        String encPwd = OpenBSDBCrypt.generate(password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 12);

        store.storeUser(0, username, encPwd);
    }

    public UserDao login(String username, String password) {
        Optional<UserDao> user = store.findUser(username);
        if (!user.isPresent()) {
            throw new ForbiddenException("Invalid credentials");
        }

        String pass = user.get().getPass();
        if (StringUtils.isEmpty(pass)) {
            throw new ForbiddenException("Invalid credentials");
        }

        boolean isValid = OpenBSDBCrypt.checkPassword(pass, password.toCharArray());
        if (!isValid) {
            throw new ForbiddenException("Invalid credentials");
        }

        return user.get();
    }

    public void addThreepid(long userLid, ThreePid tpid) {
        store.addThreePid(userLid, tpid);
    }

}
