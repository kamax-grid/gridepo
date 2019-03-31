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

package io.kamax.test.grid.gridepo.core;

import io.kamax.grid.gridepo.core.EntityID;
import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EntityIDTest {

    private final String sigill = "|";
    private final String local = "a";
    private final String localUtf8 = "日本語";
    private final String full = sigill + local;

    @Test
    public void create() {
        EntityID id = new EntityID(sigill, local);
        assertEquals(sigill, id.sigill());
        assertEquals(local, id.base());
        assertEquals(full, id.full());
        assertEquals(full, id.toString());
    }

    @Test
    public void equals() {
        EntityID id1 = new EntityID(sigill, local);
        EntityID id2 = new EntityID(sigill, local);
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    public void decodeValid() {
        String encoded = Base64.encodeBase64URLSafeString(localUtf8.getBytes(StandardCharsets.UTF_8));
        EntityID id = new EntityID(sigill, encoded);
        Optional<String> decoded = id.tryDecode();
        assertTrue(decoded.isPresent());
        assertEquals(localUtf8, decoded.get());
    }

    @Test
    public void decodeInvalid() {
        EntityID id = new EntityID(sigill, local);
        assertFalse(id.tryDecode().isPresent());
    }

}
