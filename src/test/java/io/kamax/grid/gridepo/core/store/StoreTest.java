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

package io.kamax.grid.gridepo.core.store;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public abstract class StoreTest {

    private Store store;

    protected abstract Store getNewStore();

    @Before
    public void before() {
        store = getNewStore();
    }

    @Test
    public void newUserDoesNotExistInNewStore() {
        String user = RandomStringUtils.random(12);
        assertFalse(store.hasUser(user));
        assertFalse(store.findPassword(user).isPresent());
    }

    @Test
    public void userSavedAndRead() {
        String user = RandomStringUtils.random(12);
        String password = RandomStringUtils.random(12);
        store.storeUser(user, password);

        assertTrue(store.hasUser(user));
        Optional<String> pwdStored = store.findPassword(user);
        assertTrue(pwdStored.isPresent());
        assertEquals(password, pwdStored.get());
    }

    @Test
    public void onlySavedUsersAreFound() {
        String user1 = RandomStringUtils.random(12);
        String user2 = RandomStringUtils.random(12);
        String user3 = RandomStringUtils.random(12);

        store.storeUser(user1, user1);
        store.storeUser(user2, user2);

        assertTrue(store.hasUser(user1));
        Optional<String> u1PwdStored = store.findPassword(user1);
        assertTrue(u1PwdStored.isPresent());
        assertEquals(user1, u1PwdStored.get());

        assertTrue(store.hasUser(user2));
        Optional<String> u2PwdStored = store.findPassword(user2);
        assertTrue(u2PwdStored.isPresent());
        assertEquals(user2, u2PwdStored.get());

        assertFalse(store.hasUser(user3));
        assertFalse(store.findPassword(user3).isPresent());
    }

}
