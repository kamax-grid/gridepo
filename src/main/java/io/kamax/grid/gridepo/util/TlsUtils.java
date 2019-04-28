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

package io.kamax.grid.gridepo.util;

import org.apache.http.ssl.SSLContextBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemReader;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.stream.Collectors;

public class TlsUtils {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static List<X509Certificate> load(String path) {
        try (FileInputStream is = new FileInputStream(path)) {
            return CertificateFactory.getInstance("X.509")
                    .generateCertificates(is).stream()
                    .map(c -> (X509Certificate) c)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static SSLContext buildContext(String keyFilePath, String certFilePath) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
            PemReader key = new PemReader(new FileReader(keyFilePath));

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key.readPemObject().getContent());
            PrivateKey privKey = factory.generatePrivate(keySpec);

            List<X509Certificate> certC = load(certFilePath);
            Certificate[] certs = new Certificate[certC.size()];
            certC.toArray(certs);
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, "".toCharArray());
            store.setKeyEntry("cert", privKey, "".toCharArray(), certs);
            return SSLContextBuilder.create().loadKeyMaterial(store, "".toCharArray()).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
