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

package io.kamax.grid.gridepo.core.identity.store.ldap;

import com.google.gson.JsonObject;
import io.kamax.grid.GenericThreePid;
import io.kamax.grid.ThreePid;
import io.kamax.grid.gridepo.config.Identity.store.LdapConfig;
import io.kamax.grid.gridepo.core.auth.AuthPasswordDocument;
import io.kamax.grid.gridepo.core.auth.AuthResult;
import io.kamax.grid.gridepo.core.identity.AuthIdentityStore;
import io.kamax.grid.gridepo.core.identity.EntityProfile;
import io.kamax.grid.gridepo.core.identity.IdentityStore;
import io.kamax.grid.gridepo.core.identity.ProfileIdentityStore;
import io.kamax.grid.gridepo.exception.ConfigurationException;
import io.kamax.grid.gridepo.exception.InternalServerError;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.CursorLdapReferralException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class LdapIdentityStore implements IdentityStore, AuthIdentityStore, ProfileIdentityStore {

    private static final Logger log = LoggerFactory.getLogger(LdapIdentityStore.class);

    public interface LdapServerFunction<LdapServer, R> {

        R apply(LdapServer srv) throws CursorException, LdapException, IOException;

    }

    private LdapConfig cfg;
    private List<LdapServer> servers = new ArrayList<>();

    public LdapIdentityStore(LdapConfig cfg) {
        this.cfg = cfg;

        if (cfg.getConnection().getServers().isEmpty()) {
            throw new ConfigurationException("No LDAP server configured");
        }

        if (getBaseDNs().isEmpty()) {
            throw new ConfigurationException("No Base DNs configured");
        }

        cfg.getConnection().getServers().forEach(connCfg -> servers.add(new LdapServer(connCfg, cfg.getConnection().getBind(), getBaseDNs())));


    }

    private <T> T onAllServers(LdapServerFunction<LdapServer, T> f) {
        for (LdapServer srv : servers) {
            try {
                return f.apply(srv);
            } catch (IOException e) {
                log.warn("LDAP server at {} failed to be used due to an I/O error: {}", srv.getLabel(), e.getMessage());
            } catch (CursorException | LdapException e) {
                throw new InternalServerError("Error when performing LDAP call on " + srv.getLabel(), e);
            }
        }

        throw new InternalServerError("No available LDAP server to perform request");
    }

    protected List<String> getBaseDNs() {
        return cfg.getBaseDNs();
    }

    protected LdapConfig.Attribute getAt() {
        return cfg.getAttribute();
    }

    protected String getAtUid() {
        return StringUtils.defaultIfEmpty(getAt().getUid(), "cn");
    }

    protected String buildWithFilter(String base, String filter) {
        if (StringUtils.isBlank(filter)) {
            return base;
        } else {
            return "(&" + filter + base + ")";
        }
    }

    public static String buildOrQuery(String value, List<String> attributes) {
        if (attributes.size() < 1) {
            throw new IllegalArgumentException();
        }

        StringBuilder builder = new StringBuilder();
        builder.append("(|");
        attributes.forEach(s -> {
            builder.append("(");
            builder.append(s).append("=").append(value).append(")");
        });
        builder.append(")");
        return builder.toString();
    }

    public String buildOrQueryWithFilter(String filter, String value, List<String> attributes) {
        return buildWithFilter(buildOrQuery(value, attributes), filter);
    }

    public Optional<String> getAttribute(Entry entry, String attName) {
        Attribute attribute = entry.get(attName);
        if (attribute == null) {
            return Optional.empty();
        }

        String value = attribute.get().toString();
        if (StringUtils.isBlank(value)) {
            log.info("DN {}: empty attribute {}, skipping", entry.getDn().getName(), attName);
            return Optional.empty();
        }

        return Optional.of(value);
    }

    @Override
    public Set<String> getSupportedTypes() {
        return Collections.singleton("g.auth.id.password");
    }

    @Override
    public Optional<AuthResult> authenticate(String type, JsonObject document) {
        AuthPasswordDocument credentials = AuthPasswordDocument.from(document);
        if (!StringUtils.equals("g.auth.id.password", credentials.getType())) {
            throw new NotImplementedException();
        }

        String idType = credentials.getIdentifier().getType();
        List<String> attributes = cfg.getIdentity().getMapping().getOrDefault(idType, Collections.emptyList());
        if (attributes.isEmpty()) {
            throw new NotImplementedException("No attribute mapped to identifier type " + idType);
        }

        String query = buildOrQueryWithFilter(cfg.getEntity().getUser().getFilter(), credentials.getIdentifier().getValue(), attributes);

        return onAllServers(srv -> srv.withConn(conn -> {
            for (String baseDN : getBaseDNs()) {
                try (EntryCursor cursor = conn.search(baseDN, query, SearchScope.SUBTREE, getAtUid())) {
                    while (cursor.next()) {
                        Entry entry = cursor.get();
                        String dn = entry.getDn().getName();
                        log.info("Checking possible match, DN: {}", dn);

                        Optional<String> uid = getAttribute(entry, getAtUid());
                        if (!uid.isPresent()) {
                            log.info("Attribute {} not  present", getAtUid());
                            continue;
                        }

                        log.info("Attempting authentication on LDAP for {}", dn);
                        try {
                            conn.bind(entry.getDn(), credentials.getPassword());
                        } catch (LdapException e) {
                            log.info("Unable to bind using {} because {}", entry.getDn().getName(), e.getMessage());
                            return Optional.of(AuthResult.failed());
                        }

                        log.info("Authentication successful for {}", entry.getDn().getName());
                        log.info("DN {} is a valid match", dn);

                        return Optional.of(AuthResult.success(new GenericThreePid("g.id.local.store.ldap." + getAtUid(), uid.get())));
                    }
                } catch (CursorLdapReferralException e) {
                    log.warn("Skipping an entry that is only available via referral");
                }
            }

            log.info("No match found");
            return Optional.empty();
        }));
    }


    @Override
    public Optional<EntityProfile> findProfile(ThreePid uid) {
        return Optional.empty();
    }

    @Override
    public String getType() {
        return "ldap";
    }

    @Override
    public AuthIdentityStore forAuth() {
        return this;
    }

    @Override
    public ProfileIdentityStore forProfile() {
        return this;
    }

}
