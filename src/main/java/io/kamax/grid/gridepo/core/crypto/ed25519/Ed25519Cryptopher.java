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

package io.kamax.grid.gridepo.core.crypto.ed25519;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.codec.GridJson;
import io.kamax.grid.gridepo.core.crypto.Key;
import io.kamax.grid.gridepo.core.crypto.Signature;
import io.kamax.grid.gridepo.core.crypto.*;
import io.kamax.grid.gridepo.core.store.crypto.KeyStore;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.*;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class Ed25519Cryptopher implements Cryptopher {

    private static final Logger log = LoggerFactory.getLogger(Ed25519Cryptopher.class);

    private final EdDSAParameterSpec keySpecs;
    private final KeyStore store;

    public Ed25519Cryptopher(KeyStore store) {
        this.keySpecs = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        this.store = store;

        if (!store.getCurrentKey().isPresent()) {
            List<KeyIdentifier> keys = store.list(KeyType.Regular).stream()
                    .map(this::getKey)
                    .filter(Key::isValid)
                    .map(Key::getId)
                    .collect(Collectors.toList());

            if (keys.isEmpty()) {
                keys.add(generateKey(KeyType.Regular));
            }

            store.setCurrentKey(keys.get(0));
        }
    }

    private String generateId() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(Instant.now().toEpochMilli() - 1546297200000L); // TS since 2019-01-01T00:00:00Z to keep IDs short
        return Base64.encodeBase64URLSafeString(buffer.array()) + RandomStringUtils.randomAlphanumeric(1);
    }

    private String getPrivateKeyBase64(EdDSAPrivateKey key) {
        return Base64.encodeBase64URLSafeString(key.getSeed());
    }

    EdDSAParameterSpec getKeySpecs() {
        return keySpecs;
    }

    @Override
    public KeyIdentifier generateKey(KeyType type) {
        KeyIdentifier id;
        do {
            id = new GenericKeyIdentifier(type, KeyAlgorithm.Ed25519, generateId());
        } while (store.has(id));

        KeyPair pair = (new KeyPairGenerator()).generateKeyPair();
        String keyEncoded = getPrivateKeyBase64((EdDSAPrivateKey) pair.getPrivate());

        Key key = new GenericKey(id, true, keyEncoded);
        store.add(key);

        return id;
    }

    @Override
    public List<KeyIdentifier> getKeys(KeyType type) {
        return store.list(type);
    }

    @Override
    public Key getServerSigningKey() {
        return store.get(store.getCurrentKey().orElseThrow(IllegalStateException::new));
    }

    @Override
    public Key getKey(KeyIdentifier id) {
        return store.get(id);
    }

    private EdDSAPrivateKeySpec getPrivateKeySpecs(KeyIdentifier id) {
        return new EdDSAPrivateKeySpec(Base64.decodeBase64(getKey(id).getPrivateKeyBase64()), keySpecs);
    }

    EdDSAPrivateKey getPrivateKey(KeyIdentifier id) {
        return new EdDSAPrivateKey(getPrivateKeySpecs(id));
    }

    private EdDSAPublicKey getPublicKey(KeyIdentifier id) {
        EdDSAPrivateKeySpec privKeySpec = getPrivateKeySpecs(id);
        EdDSAPublicKeySpec pubKeySpec = new EdDSAPublicKeySpec(privKeySpec.getA(), keySpecs);
        return new EdDSAPublicKey(pubKeySpec);
    }

    @Override
    public void disableKey(KeyIdentifier id) {
        Key key = store.get(id);
        key = new GenericKey(id, false, key.getPrivateKeyBase64());
        store.update(key);
    }

    @Override
    public String getPublicKeyBase64(KeyIdentifier id) {
        return Base64.encodeBase64URLSafeString(getPublicKey(id).getAbyte());
    }

    @Override
    public boolean isValid(KeyType type, String publicKeyBase64) {
        // TODO caching?
        return getKeys(type).stream().anyMatch(id -> StringUtils.equals(getPublicKeyBase64(id), publicKeyBase64));
    }

    @Override
    public Signature sign(JsonObject obj, KeyIdentifier keyId) {
        return sign(GridJson.encodeCanonical(obj), keyId);
    }

    @Override
    public Signature sign(byte[] data, KeyIdentifier keyId) {
        try {
            KeyIdentifier signingKeyId = getServerSigningKey().getId();
            EdDSAEngine signEngine = new EdDSAEngine(MessageDigest.getInstance(getKeySpecs().getHashAlgorithm()));
            signEngine.initSign(getPrivateKey(signingKeyId));
            byte[] signRaw = signEngine.signOneShot(data);
            String sign = Base64.encodeBase64URLSafeString(signRaw);

            return new Signature() {
                @Override
                public KeyIdentifier getKey() {
                    return signingKeyId;
                }

                @Override
                public String getSignature() {
                    return sign;
                }
            };
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public KeyIdentifier getKeyWithPublic(String pubKeyBase64) {
        if (StringUtils.isBlank(pubKeyBase64)) {
            throw new IllegalArgumentException("Invalid public key: " + pubKeyBase64);
        }

        for (KeyIdentifier id : store.list()) {
            if (StringUtils.equals(getPublicKeyBase64(id), pubKeyBase64)) {
                return id;
            }
        }

        throw new ObjectNotFoundException("No keypair with matching public key " + pubKeyBase64);
    }

}
