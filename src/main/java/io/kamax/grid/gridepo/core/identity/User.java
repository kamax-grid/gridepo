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

import io.kamax.grid.gridepo.core.UserID;
import io.kamax.grid.gridepo.core.crypto.Cryptopher;
import io.kamax.grid.gridepo.core.crypto.KeyIdentifier;
import io.kamax.grid.gridepo.core.crypto.KeyType;
import io.kamax.grid.gridepo.core.store.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

public class User {

    private static final Logger log = LoggerFactory.getLogger(User.class);

    private long lid;
    private String id;
    private DataStore store;
    private Cryptopher keyring;

    public User(long lid, String id, DataStore store, Cryptopher keyring) {
        this.lid = lid;
        this.id = id;
        this.store = store;
        this.keyring = keyring;
    }

    public long getLid() {
        return lid;
    }

    public String getId() {
        return id;
    }

    public Set<KeyIdentifier> getKeys() {
        return store.listThreePid(lid, "g.id.key.ed25519").stream()
                .map(tpid -> keyring.getKeyWithPublic(tpid.getAddress()))
                .collect(Collectors.toSet());
    }

    public KeyIdentifier generateKey() {
        KeyIdentifier keyId = keyring.generateKey(KeyType.Regular);
        String pubKeyId = keyring.getPublicKeyBase64(keyId);
        ThreePid tpid = new GenericThreePid("g.id.key.ed25519", pubKeyId);
        ThreePid tpidGrid = new GenericThreePid("g.id.net.grid", new UserID(pubKeyId).full());
        addThreePid(tpid);
        addThreePid(tpidGrid);
        return keyId;
    }

    public void linkToStoreId(ThreePid tpid) {
        if (!tpid.getMedium().startsWith("g.id.local.store.")) {
            throw new IllegalArgumentException("A store ID must use the namespace g.id.local.store");
        }

        store.linkUserToStore(lid, tpid);
    }

    public void addThreePid(ThreePid tpid) {
        store.addThreePid(lid, tpid);
        log.info("LID {}: 3PID: add: {}", lid, tpid);
    }

    public void removeThreePid(ThreePid tpid) {
        store.removeThreePid(lid, tpid);
    }

    public UserID getGridId() {
        return store.listThreePid(lid, "g.id.net.grid").stream()
                .findFirst()
                .map(id -> UserID.parse(id.getAddress()))
                .orElseThrow(IllegalStateException::new);
    }

}
