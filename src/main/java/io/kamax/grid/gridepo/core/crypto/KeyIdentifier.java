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

package io.kamax.grid.gridepo.core.crypto;

/**
 * Identifying data for a given Key.
 */
public interface KeyIdentifier {

    /**
     * Type of key.
     *
     * @return The type of the key
     */
    KeyType getType();

    /**
     * Algorithm of the key. Typically <code>ed25519</code>.
     *
     * @return The algorithm of the key
     */
    String getAlgorithm();

    /**
     * Serial of the key, unique for the algorithm.
     * It is typically made of random alphanumerical characters.
     *
     * @return The serial of the key
     */
    String getSerial();

    default String getId() {
        return getAlgorithm().toLowerCase() + ":" + getSerial();
    }

}
