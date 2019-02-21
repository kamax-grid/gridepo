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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.codec.GridJson;
import io.kamax.grid.gridepo.exception.ForbiddenException;
import io.kamax.grid.gridepo.exception.RemoteServerException;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class DataServerClient {

    private final Logger log = LoggerFactory.getLogger(DataServerClient.class);

    private String source;
    private CloseableHttpClient client;

    public DataServerClient(String source) {
        this.source = source;

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

    private int port = 9449;
    private String prefix = "_grid._tcp.";

    private List<Address> lookupSrv(String domain) {
        List<Address> addrs = new ArrayList<>();
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
            log.warn("Invalid SRV reccords: {}", e.getMessage());
        }

        return addrs;
    }

    protected HttpEntity getJsonEntity(Object o) {
        return EntityBuilder.create()
                .setText(GsonUtil.get().toJson(o))
                .setContentType(ContentType.APPLICATION_JSON)
                .build();
    }

    private String getAuthObj(String remoteDomain, String method, URI target) {
        return getAuthObj(remoteDomain, method, target, null);
    }

    private String getAuthObj(String remoteDomain, String method, URI target, JsonElement content) {
        String uri = target.getRawPath();
        if (StringUtils.isNotBlank(target.getRawQuery())) {
            uri += "?" + target.getRawQuery();
        }

        JsonObject authObj = new JsonObject();
        authObj.addProperty("method", method);
        authObj.addProperty("uri", uri);
        authObj.addProperty("origin", source);
        authObj.addProperty("destination", remoteDomain);
        Optional.ofNullable(content).ifPresent(c -> authObj.add("content", c));
        String data = GridJson.encodeCanonical(authObj);
        log.debug("Auth object: {}", data);
        return data;
    }

    private JsonObject getBody(HttpEntity entity) throws IOException {
        Charset charset = ContentType.getOrDefault(entity).getCharset();
        String raw = IOUtils.toString(entity.getContent(), charset);
        entity.getContent().close();
        if (raw.isEmpty()) {
            return new JsonObject();
        }

        return GsonUtil.parseObj(raw);
    }

    /*
    private JsonObject sendGet(URIBuilder target) {
        try {
            if (!target.getScheme().equals("matrix")) {
                throw new IllegalArgumentException("Scheme can only be matrix");
            }

            String domain = target.getHost();
            target.setScheme("https");
            IRemoteAddress addr = resolver.resolve(target.getHost());
            target.setHost(addr.getHost());
            target.setPort(addr.getPort());

            return sendGet(domain, target.build());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject sendGet(String domain, URI target) {
        String authObj = getAuthObj(domain, "GET", target);
        String sign = global.getSignMgr().sign(authObj);
        String key = "ed25519:" + global.getKeyMgr().getCurrentIndex();

        HttpGet req = new HttpGet(target);
        req.setHeader("Host", domain);
        req.setHeader("Authorization",
                "X-Matrix origin=" + global.getDomain() + ",key=\"" + key + "\",sig=\"" + sign + "\"");
        log.info("Calling [{}] {}", domain, req);
        try (CloseableHttpResponse res = client.execute(req)) {
            int resStatus = res.getStatusLine().getStatusCode();
            JsonObject body = getBody(res.getEntity());
            if (resStatus == 200) {
                log.info("Got answer");
                return body;
            } else {
                throw new FederationException(resStatus, body);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject sendPost(URIBuilder target, JsonElement payload) {
        try {
            if (!target.getScheme().equals("matrix")) {
                throw new IllegalArgumentException("Scheme can only be matrix");
            }

            String domain = target.getHost();
            target.setScheme("https");
            IRemoteAddress addr = resolver.resolve(target.getHost());
            target.setHost(addr.getHost());
            target.setPort(addr.getPort());

            return sendPost(domain, target.build(), payload);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject sendPost(String domain, URI target, JsonElement payload) {
        String authObj = getAuthObj(domain, "POST", target, payload);
        String sign = global.getSignMgr().sign(authObj);
        String key = "ed25519:" + global.getKeyMgr().getCurrentIndex();

        HttpPost req = new HttpPost(target);
        req.setEntity(getJsonEntity(payload));
        req.setHeader("Host", domain);
        req.setHeader("Authorization",
                "X-Matrix origin=" + domain + ",key=\"" + key + "\",sig=\"" + sign + "\"");
        log.info("Calling [{}] {}", domain, req);
        log.debug("Payload: {}", GsonUtil.getPrettyForLog(payload));
        try (CloseableHttpResponse res = client.execute(req)) {
            int resStatus = res.getStatusLine().getStatusCode();
            JsonObject body = getBody(res.getEntity());
            if (resStatus == 200) {
                log.info("Got answer");
                return body;
            } else {
                throw new FederationException(resStatus, body);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject sendPut(URIBuilder target, JsonElement payload) {
        try {
            if (!target.getScheme().equals("matrix")) {
                throw new IllegalArgumentException("Scheme can only be matrix");
            }

            String domain = target.getHost();
            target.setScheme("https");
            IRemoteAddress addr = resolver.resolve(target.getHost());
            target.setHost(addr.getHost());
            target.setPort(addr.getPort());

            return sendPut(domain, target.build(), payload);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject sendPut(String domain, URI target, JsonElement payload) {
        String authObj = getAuthObj(domain, "PUT", target, payload);
        String sign = global.getSignMgr().sign(authObj);
        String key = "ed25519:" + global.getKeyMgr().getCurrentIndex();

        HttpPut req = new HttpPut(target);
        req.setEntity(getJsonEntity(payload));
        req.setHeader("Host", domain);
        req.setHeader("Authorization",
                "X-Matrix origin=" + global.getDomain() + ",key=\"" + key + "\",sig=\"" + sign + "\"");
        log.info("Calling [{}] {}", domain, req);
        log.debug("Payload: {}", GsonUtil.getPrettyForLog(payload));
        try (CloseableHttpResponse res = client.execute(req)) {
            int resStatus = res.getStatusLine().getStatusCode();
            JsonObject body = getBody(res.getEntity());
            if (resStatus == 200) {
                log.info("Got answer");
                return body;
            } else {
                throw new FederationException(resStatus, body);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject sendDelete(String domain, String path, JsonObject playload) {
        throw new NotImplementedException("");
    }

    private URIBuilder getUri(String domain, String path) {
        return new URIBuilder(URI.create("matrix://" + domain + path));
    }

    private URIBuilder getUri(String domain, String path, Map<String, String> parameters) {
        URIBuilder b = new URIBuilder(URI.create("matrix://" + domain + path));
        parameters.forEach(b::addParameter);
        return b;
    }

    public JsonObject send(String domain, String method, String url, Map<String, String> parameters, JsonElement body) {
        URIBuilder b = getUri(domain, url);
        if (parameters != null && !parameters.isEmpty()) {
            parameters.forEach(b::addParameter);
        }

        if (StringUtils.equals("GET", method)) {
            if (body != null) {
                throw new IllegalArgumentException("Cannot send body with GET");
            }

            return sendGet(b);
        } else if (StringUtils.equals("POST", method)) {
            return sendPost(b, body);
        } else if (StringUtils.equals("PUT", method)) {
            return sendPut(b, body);
        } else {
            throw new NotImplementedException(method);
        }
    }

    public JsonObject makeJoin(String residentHsDomain, String roomId, _MatrixID joiner) {
        // FIXME refactor URL from Spring classes
        return sendGet(getUri(residentHsDomain, "/_matrix/federation/v1/make_join/" + roomId + "/" + joiner.getId()));
    }

    public JsonObject sendJoin(String residentHsDomain, IEvent ev) {
        // FIXME refactor URL from Spring classes
        return sendPut(getUri(residentHsDomain, "/_matrix/federation/v1/send_join/" + ev.getRoomId() + "/" + ev.getId()), ev.getJson());
    }

    public JsonObject sendTransaction(String domain, String id, JsonObject o) {
        return sendPut(getUri(domain, "/_matrix/federation/v1/send/" + id + "/"), o);
    }

    public JsonObject getRoomState(String domain, String roomId, String eventId) {
        return sendGet(getUri(domain, "/_matrix/federation/v1/state/" + roomId + "/", Collections.singletonMap("event_id", eventId)));
    }

    public JsonObject getRoomStateIds(String domain, String roomId, String eventId) {
        return sendGet(getUri(domain, "/_matrix/federation/v1/state_ids/" + roomId + "/", Collections.singletonMap("event_id", eventId)));
    }

    public JsonObject getMissingEvents(String domain, String roomId, List<EventUID> earliest, List<EventUID> latest) {
        JsonArray earliestJson = GsonUtil.asArray(earliest.stream().map(EventUID::getEventId).collect(Collectors.toList()));
        JsonArray latestJson = GsonUtil.asArray(latest.stream().map(EventUID::getEventId).collect(Collectors.toList()));
        JsonObject body = new JsonObject();
        body.add("earliest_events", earliestJson);
        body.add("latest_events", latestJson);
        return sendPost(getUri(domain, "/_matrix/federation/v1/get_missing_events/" + roomId), body);
    }

    public JsonObject getEvent(String domain, String id) {
        throw new NotImplementedException("");
    }

    public JsonObject backfill(String domain, String fromEventId, long limit) {
        throw new NotImplementedException("");
    }

    public JsonObject frontfill(String domain, String fromEventId, long limit) {
        throw new NotImplementedException("");
    }

    public JsonObject query(String domain, String type, Map<String, String> parameters) {
        // FIXME refactor URL from Spring classes
        URIBuilder b = getUri(domain, "/_matrix/federation/v1/query/" + type);
        if (parameters != null && !parameters.isEmpty()) {
            parameters.forEach(b::addParameter);
        }
        return sendGet(b);
    }
    */

    public JsonObject approveEvent(String domain, JsonObject data) {
        HttpPost req = new HttpPost();
        req.setHeader("Content-Type", "application/json");
        req.setEntity(getJsonEntity(data));

        for (Address ad : lookupSrv(domain)) {
            String srvUriRaw = "https://" + ad.getHost() + ":" + ad.getPort() + "/server/v0/do/event/approve";
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
                                throw new ForbiddenException(GsonUtil.findString(b, "message").orElse("Server did not give a reason"));
                            }

                            throw new RemoteServerException(
                                    domain,
                                    GsonUtil.findString(b, "code").orElse("G_UNKNOWN"),
                                    GsonUtil.findString(b, "message").orElse("Server did not return a valid error message")
                            );
                        }

                        try {
                            // FIXME check if the server signed the event before returning it
                            return GsonUtil.parseObj(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
                        } catch (IllegalArgumentException e) {
                            throw new RemoteServerException(domain, "G_REMOTE_ERROR", "Server did not send us back JSON");
                        }
                    }
                } catch (IOException e) {
                    log.warn("");
                }
            } catch (URISyntaxException e) {
                log.warn("Unable to create URI for server: Invalid URI for {}: {}", srvUriRaw, e.getMessage());
            }
        }

        throw new RemoteServerException(domain, "G_FEDERATION_ERROR", "Could not find a working server for " + domain);
    }

}
