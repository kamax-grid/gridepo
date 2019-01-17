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

package io.kamax.grid.gridepo.http;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.core.MonolithGridepo;
import io.kamax.grid.gridepo.util.TlsUtils;
import io.undertow.Handlers;
import io.undertow.Undertow;
import org.apache.http.ssl.SSLContextBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemReader;

import javax.net.ssl.SSLContext;
import java.io.FileReader;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

public class MonolithHttpGridepo implements Gridepo {

    private GridepoConfig cfg;
    private MonolithGridepo g;
    private Undertow u;

    public MonolithHttpGridepo(GridepoConfig cfg) {
        this.cfg = cfg;
    }

    private SSLContext buildTls(String keyFilePath, String certFilePath) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
            PemReader key = new PemReader(new FileReader(keyFilePath));

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key.readPemObject().getContent());
            PrivateKey privateKey = factory.generatePrivate(keySpec);

            X509Certificate certC = TlsUtils.load(certFilePath);
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, "".toCharArray());
            store.setKeyEntry("federation", privateKey, "".toCharArray(), new Certificate[]{certC});
            return SSLContextBuilder.create().loadKeyMaterial(store, "".toCharArray()).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void build() {
        g = new MonolithGridepo(cfg);

        SSLContext sslC = buildTls(cfg.getFederation().getKey(), cfg.getFederation().getCert());
        Undertow.Builder b = Undertow.builder();
        b.addHttpsListener(cfg.getFederation().getPort(), cfg.getFederation().getIp(), sslC).setHandler(Handlers.routing());
        b.addHttpListener(cfg.getClient().getPort(), cfg.getClient().getIp()).setHandler(Handlers.routing());
        u = b.build();
    }

    @Override
    public void start() {
        build();

        g.start();
        u.start();
    }

    @Override
    public void stop() {
        u.stop();
        g.stop();
    }

}
