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

package io.kamax.grid.gridepo.config;

import io.kamax.grid.gridepo.core.store.MemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class GridepoConfig {

    public static GridepoConfig inMemory() {
        String uuid = UUID.randomUUID().toString();

        GridepoConfig cfg = new GridepoConfig();
        cfg.getStorage().getDatabase().setType("memory");
        cfg.getStorage().getDatabase().setConnection(uuid);
        cfg.getStorage().getKey().setType("memory");
        cfg.getStorage().setData(uuid);
        cfg.getIdentity().getStores().put("memory", MemoryStore.getMinimalConfig(uuid));
        cfg.getAuth().addFlow().addStage("g.auth.id.password");

        return cfg;
    }

    public static class NetworkListeners {

        public static NetworkListener forGridDataServer() {
            return NetworkListener.build("grid", "data", "server");
        }

        public static NetworkListener forGridIdentityServer() {
            return NetworkListener.build("grid", "identity", "server");
        }

    }

    public static class NetworkListener {

        public static NetworkListener build(String protocol, String role, String api) {
            NetworkListener v = new NetworkListener();
            v.setProtocol(protocol);
            v.setRole(role);
            v.setApi(api);
            return v;
        }

        private String protocol;
        private String role;
        private String api;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getApi() {
            return api;
        }

        public void setApi(String api) {
            this.api = api;
        }
    }

    public static class Listener {

        private List<NetworkListener> network;
        private String address = "0.0.0.0";
        private int port;
        private boolean tls;
        private String key;
        private String cert;

        public List<NetworkListener> getNetwork() {
            return network;
        }

        public void setNetwork(List<NetworkListener> network) {
            this.network = network;
        }

        public void addNetwork(NetworkListener network) {
            if (Objects.isNull(this.network)) {
                this.network = new ArrayList<>();
            }

            this.network.add(network);
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isTls() {
            return tls;
        }

        public void setTls(boolean tls) {
            this.tls = tls;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getCert() {
            return cert;
        }

        public void setCert(String cert) {
            this.cert = cert;
        }

    }

    private List<Listener> listeners = new ArrayList<>();
    private CryptoConfig crypto = new CryptoConfig();
    private String domain;
    private ChannelConfig channel = new ChannelConfig();
    private StorageConfig storage = new StorageConfig();
    private IdentityConfig identity = new IdentityConfig();
    private UIAuthConfig auth = new UIAuthConfig();

    public List<Listener> getListeners() {
        return listeners;
    }

    public void setListeners(List<Listener> listeners) {
        this.listeners = listeners;
    }

    public CryptoConfig getCrypto() {
        return crypto;
    }

    public void setCrypto(CryptoConfig crypto) {
        this.crypto = crypto;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public ChannelConfig getChannel() {
        return channel;
    }

    public void setChannel(ChannelConfig channel) {
        this.channel = channel;
    }

    public StorageConfig getStorage() {
        return storage;
    }

    public void setStorage(StorageConfig storage) {
        this.storage = storage;
    }

    public IdentityConfig getIdentity() {
        return identity;
    }

    public void setIdentity(IdentityConfig identity) {
        this.identity = identity;
    }

    public UIAuthConfig getAuth() {
        return auth;
    }

    public void setAuth(UIAuthConfig auth) {
        this.auth = auth;
    }

}
