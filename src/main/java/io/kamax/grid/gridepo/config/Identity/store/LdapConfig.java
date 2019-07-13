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

package io.kamax.grid.gridepo.config.Identity.store;

import java.util.*;

public abstract class LdapConfig {

    public static class Attribute {

        private String uid;
        private Map<String, List<String>> threepid = new HashMap<>();

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public Map<String, List<String>> getThreepid() {
            return threepid;
        }

        public void setThreepid(Map<String, List<String>> threepid) {
            this.threepid = threepid;
        }

    }

    public static class Auth {

        private String filter;

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

    }

    public static class ConnServer {

        private String host;
        private int port = 389;
        private boolean tls = false;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
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

    }

    public static class ConnBind {

        private String dn;
        private String password;

        public String getDn() {
            return dn;
        }

        public void setDn(String dn) {
            this.dn = dn;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

    }

    public static class Connection {

        private List<ConnServer> server = new ArrayList<>();
        private ConnBind bind = new ConnBind();
        private List<String> baseDNs = new ArrayList<>();

        public List<ConnServer> getServer() {
            return server;
        }

        public void setServer(List<ConnServer> server) {
            this.server = server;
        }

        public ConnBind getBind() {
            return bind;
        }

        public void setBind(ConnBind bind) {
            this.bind = bind;
        }

        public List<String> getBaseDNs() {
            return baseDNs;
        }

        public void setBaseDNs(List<String> baseDNs) {
            this.baseDNs = baseDNs;
        }

    }

    public static class Directory {

        public static class Attribute {

            private List<String> extra = new ArrayList<>();

            public List<String> getExtra() {
                return extra;
            }

            public void setExtra(List<String> extra) {
                this.extra = extra;
            }

        }

        private Attribute attribute = new Attribute();
        private String filter;

        public Attribute getAttribute() {
            return attribute;
        }

        public void setAttribute(Attribute attribute) {
            this.attribute = attribute;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

    }

    public static class Profile {

        private String filter;

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

    }

    public static class Entity {

        private String filter;
        private String memberOf;

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

        public String getMemberOf() {
            return memberOf;
        }

        public void setMemberOf(String memberOf) {
            this.memberOf = memberOf;
        }

    }

    public static class Entities {

        private Entity user = new Entity();
        private Entity group = new Entity();

        public Entity getUser() {
            return user;
        }

        public void setUser(Entity user) {
            this.user = user;
        }

        public Entity getGroup() {
            return group;
        }

        public void setGroup(Entity group) {
            this.group = group;
        }

    }

    public class Identity {

        private Map<String, List<String>> mapping;

        public Map<String, List<String>> getMapping() {
            return (Objects.isNull(mapping) ? getDefaultIdentityMappings() : mapping);
        }

        public void setMapping(Map<String, List<String>> mapping) {
            this.mapping = mapping;
        }

    }

    private Connection connection = new Connection();
    private Entities entity = new Entities();
    private Attribute attribute = new Attribute();
    private Auth auth = new Auth();
    private Directory directory = new Directory();
    private Identity identity = new Identity();
    private Profile profile = new Profile();

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection conn) {
        this.connection = conn;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public void setAttribute(Attribute attribute) {
        this.attribute = attribute;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Directory getDirectory() {
        return directory;
    }

    public void setDirectory(Directory directory) {
        this.directory = directory;
    }

    public Entities getEntity() {
        return entity;
    }

    public void setEntity(Entities entity) {
        this.entity = entity;
    }

    public Identity getIdentity() {
        return identity;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    protected abstract Map<String, List<String>> getDefaultIdentityMappings();

}
