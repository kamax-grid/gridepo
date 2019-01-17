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

import com.google.gson.JsonObject;
import net.i2p.crypto.eddsa.EdDSAEngine;
import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public class SignManager {

    private KeyManager keyMgr;
    private EdDSAEngine signEngine;

    public SignManager(KeyManager keyMgr) {
        this.keyMgr = keyMgr;

        try {
            signEngine = new EdDSAEngine(MessageDigest.getInstance(keyMgr.getSpecs().getHashAlgorithm()));
            signEngine.initSign(keyMgr.getPrivateKey(keyMgr.getCurrentIndex()));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public String sign(String message) {
        try {
            return Base64.encodeBase64String(signEngine.signOneShot(message.getBytes(StandardCharsets.UTF_8)));
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonObject getSignatures(String message) {
        String sign = sign(message);

        JsonObject signatures = new JsonObject();
        signatures.addProperty("ed25519:" + keyMgr.getCurrentIndex(), sign);
        return signatures;
    }

}
