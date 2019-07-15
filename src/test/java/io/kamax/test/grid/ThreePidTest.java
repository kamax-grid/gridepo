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

package io.kamax.test.grid;

import io.kamax.grid.gridepo.core.identity.GenericThreePid;
import io.kamax.grid.gridepo.core.identity.ThreePid;
import org.junit.Test;

import static org.junit.Assert.*;

public class ThreePidTest {

    private static String medium1 = "email";
    private static String address1 = "john.doe@example.org";
    private static String medium2 = "msisdn";
    private static String address2 = "123456789";

    @Test
    public void basic() {
        ThreePid tp1 = new GenericThreePid(medium1, address1);
        assertTrue(medium1.contentEquals(tp1.getMedium()));
        assertTrue(address1.contentEquals(tp1.getAddress()));
    }

    @Test
    public void equal() {
        ThreePid tp11 = new GenericThreePid(medium1, address1);
        ThreePid tp12 = new GenericThreePid(medium1, address1);
        ThreePid tp21 = new GenericThreePid(medium2, address2);
        ThreePid tp22 = new GenericThreePid(medium2, address2);

        assertEquals(tp11, tp12);
        assertEquals(tp12, tp11);
        assertEquals(tp21, tp22);
        assertEquals(tp22, tp21);
        assertNotEquals(tp11, tp21);
        assertNotEquals(tp22, tp12);
    }

}
