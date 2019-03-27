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

import java.util.ArrayList;
import java.util.List;

public class GridepoConfig {

    public static class ListenerNetwork {

        public static ListenerNetwork build(String protocol, String type) {
            ListenerNetwork v = new ListenerNetwork();
            v.setProtocol(protocol);
            v.setType(type);
            return v;
        }

        private String protocol;
        private String type;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

    }

    public static class Listener {

        private List<ListenerNetwork> network = new ArrayList<>();
        private String address = "0.0.0.0";
        private int port;

        public List<ListenerNetwork> getNetwork() {
            return network;
        }

        public void setNetwork(List<ListenerNetwork> network) {
            this.network = network;
        }

        public void addNetwork(ListenerNetwork network) {
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

    }

    private List<Listener> listeners = new ArrayList<>();
    private CryptoConfig crypto = new CryptoConfig();
    private String domain;
    private ChannelConfig channel = new ChannelConfig();
    private StorageConfig storage = new StorageConfig();
    private IdentityConfig identity = new IdentityConfig();

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

}
