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

package io.kamax.grid.gridepo.core.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.RemoteServerException;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.*;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class DataServerHttpClient implements DataServerClient {

    // FIXME pure hack, switch to config - maybe only for testing?
    public static boolean useHttps = true;

    private final Logger log = LoggerFactory.getLogger(DataServerHttpClient.class);

    private CloseableHttpClient client;

    public DataServerHttpClient() {
        try {
            // FIXME properly handle SSL context by validating certificate hostname
            SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(TrustAllStrategy.INSTANCE).build();
            HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
            this.client = HttpClientBuilder.create()
                    .disableAuthCaching()
                    .disableAutomaticRetries()
                    .disableCookieManagement()
                    .disableRedirectHandling()
                    .setSSLSocketFactory(sslSocketFactory)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(30 * 1000) // FIXME make configurable
                            .setConnectionRequestTimeout(5 * 60 * 1000) // FIXME make configurable
                            .setSocketTimeout(5 * 60 * 1000) // FIXME make configurable
                            .build())
                    .setMaxConnPerRoute(Integer.MAX_VALUE) // FIXME make configurable
                    .setMaxConnTotal(Integer.MAX_VALUE) // FIXME make configurable
                    .setUserAgent("gridepo" + "/" + "0.0.0")
                    .build();
        } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private int port = 443;
    private String prefix = "_grid._tcp.";

    private List<Address> lookupSrv(String domain) {
        List<Address> addrs = new ArrayList<>();

        int i = domain.lastIndexOf(":");
        if (i > -1) {
            // This is a literal
            addrs.add(new Address(domain.substring(0, i), Integer.parseInt(domain.substring(i + 1))));
            return addrs;
        }

        try {
            Record[] records = new Lookup(prefix + domain, Type.SRV).run();
            if (records == null) {
                // No SRV record, we return the default values
                addrs.add(new Address(domain, port));
            } else {
                // We found SRV records, processing
                Stream.of(records)
                        .filter(record -> record.getType() == Type.SRV && record instanceof SRVRecord)
                        .map(record -> (SRVRecord) record)
                        .sorted(Comparator.comparingInt(SRVRecord::getPriority))
                        .map(record -> new Address(record.getTarget().toString(true), record.getPort()))
                        .forEach(addrs::add);
            }
        } catch (TextParseException e) {
            log.warn("Invalid SRV records: {}", e.getMessage());
        }

        return addrs;
    }

    private HttpEntity getJsonEntity(Object o) {
        return EntityBuilder.create()
                .setText(GsonUtil.get().toJson(o))
                .setContentType(ContentType.APPLICATION_JSON)
                .build();
    }

    @Override
    public JsonObject push(String as, String to, List<ChannelEvent> chEvents) {
        JsonArray events = new JsonArray();
        chEvents.forEach(chEv -> events.add(chEv.getData()));

        HttpPost req = new HttpPost();
        req.setHeader("X-Grid-Remote-ID", as);
        req.setEntity(getJsonEntity(GsonUtil.makeObj("events", events)));

        for (Address ad : lookupSrv(to)) {
            String srvUriRaw = (useHttps ? "https" : "http") + "://" + ad.getHost() + ":" + ad.getPort() + "/_grid/server/v0/do/push";
            try {
                URI srvUri = new URI(srvUriRaw);
                try {
                    req.setURI(srvUri);
                    try (CloseableHttpResponse res = client.execute(req)) {
                        int sc = res.getStatusLine().getStatusCode();
                        if (sc != 200) {
                            JsonObject b;
                            try {
                                b = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                            } catch (IllegalArgumentException e) {
                                b = new JsonObject();
                            }

                            if (sc == 403) {
                                throw new ForbiddenException(GsonUtil.findString(b, "error").orElse("Server did not give a reason"));
                            }

                            throw new RemoteServerException(
                                    to,
                                    GsonUtil.findString(b, "errcode").orElse("G_UNKNOWN"),
                                    GsonUtil.findString(b, "error").orElse("Server did not return a valid error message")
                            );
                        }

                        try {
                            // FIXME check if the server signed the event before returning it
                            return GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                        } catch (IllegalArgumentException e) {
                            throw new RemoteServerException(to, "G_REMOTE_ERROR", "Server did not send us back JSON");
                        }
                    }
                } catch (IOException e) {
                    log.warn("", e);
                }
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
            }
        }

        throw new RemoteServerException(to, "G_FEDERATION_ERROR", "Could not find a working server for " + to);
    }

    @Override
    public JsonObject approveInvite(String as, String target, JsonObject data) {
        HttpPost req = new HttpPost();
        req.setHeader("X-Grid-Remote-ID", as);
        req.setEntity(getJsonEntity(data));

        for (Address ad : lookupSrv(target)) {
            String srvUriRaw = (useHttps ? "https" : "http") + "://" + ad.getHost() + ":" + ad.getPort() + "/_grid/server/v0/do/approve/invite";
            try {
                URI srvUri = new URI(srvUriRaw);
                try {
                    req.setURI(srvUri);
                    try (CloseableHttpResponse res = client.execute(req)) {
                        int sc = res.getStatusLine().getStatusCode();
                        if (sc != 200) {
                            JsonObject b;
                            try {
                                b = GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                            } catch (IllegalArgumentException e) {
                                b = new JsonObject();
                            }

                            if (sc == 403) {
                                throw new ForbiddenException(GsonUtil.findString(b, "error").orElse("Server did not give a reason"));
                            }

                            throw new RemoteServerException(
                                    target,
                                    GsonUtil.findString(b, "errcode").orElse("G_UNKNOWN"),
                                    GsonUtil.findString(b, "error").orElse("Server did not return a valid error message")
                            );
                        }

                        try {
                            // FIXME check if the server signed the event before returning it
                            return GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                        } catch (IllegalArgumentException e) {
                            throw new RemoteServerException(target, "G_REMOTE_ERROR", "Server did not send us back JSON");
                        }
                    }
                } catch (IOException e) {
                    log.warn("", e);
                }
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
            }
        }

        throw new RemoteServerException(target, "G_FEDERATION_ERROR", "Could not find a working server for " + target);
    }

}
