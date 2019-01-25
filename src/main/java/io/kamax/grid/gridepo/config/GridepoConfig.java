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

public class GridepoConfig {

    private ClientConnectorConfig client = new ClientConnectorConfig();
    private CryptoConfig crypto = new CryptoConfig();
    private String domain;
    private ChannelConfig channel = new ChannelConfig();
    private FederationConnectorConfig federation = new FederationConnectorConfig();
    private StorageConfig storage = new StorageConfig();

    public ClientConnectorConfig getClient() {
        return client;
    }

    public void setClient(ClientConnectorConfig client) {
        this.client = client;
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

    public FederationConnectorConfig getFederation() {
        return federation;
    }

    public void setFederation(FederationConnectorConfig federation) {
        this.federation = federation;
    }

    public StorageConfig getStorage() {
        return storage;
    }

    public void setStorage(StorageConfig storage) {
        this.storage = storage;
    }

}
