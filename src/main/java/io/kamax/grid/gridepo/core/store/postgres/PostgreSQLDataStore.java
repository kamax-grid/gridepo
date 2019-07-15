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

package io.kamax.grid.gridepo.core.store.postgres;

import io.kamax.grid.gridepo.config.StorageConfig;
import io.kamax.grid.gridepo.core.ChannelID;
import io.kamax.grid.gridepo.core.EventID;
import io.kamax.grid.gridepo.core.ServerID;
import io.kamax.grid.gridepo.core.auth.Credentials;
import io.kamax.grid.gridepo.core.auth.SecureCredentials;
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.identity.ThreePid;
import io.kamax.grid.gridepo.core.store.DataStore;
import io.kamax.grid.gridepo.core.store.SqlConnectionPool;
import io.kamax.grid.gridepo.core.store.UserDao;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PostgreSQLDataStore implements DataStore {

    private static final Logger log = LoggerFactory.getLogger(PostgreSQLDataStore.class);

    private interface ConnFunction<T, R> {

        R run(T connection) throws SQLException;

    }

    private interface ConnConsumer<T> {

        void run(T conn) throws SQLException;

    }

    private interface StmtFunction<T, R> {

        R run(T stmt) throws SQLException;
    }

    private interface StmtConsumer<T> {

        void run(T stmt) throws SQLException;
    }

    private SqlConnectionPool pool;

    public PostgreSQLDataStore(StorageConfig cfg) {
        this(new SqlConnectionPool(cfg));
    }

    private PostgreSQLDataStore(SqlConnectionPool pool) {
        this.pool = pool;
        withConnConsumer(conn -> conn.isValid(1000));
        log.info("Connected");

        // FIXME Temporary solution until we can directly list from the jar
        List<String> schemaUpdates = new ArrayList<>();
        schemaUpdates.add("0-init.sql");
        withConnConsumer(conn -> {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS schema (version bigint NOT NULL)");
            conn.setAutoCommit(false);

            long version = getSchemaVersion();
            log.info("Schema version: {}", version);
            log.info("Schemas to check: {}", schemaUpdates.size());
            for (String sql : schemaUpdates) {
                log.info("Processing schema update: {}", sql);
                String[] els = sql.split("-", 2);
                if (els.length < 2) {
                    log.warn("Skipping invalid schema update name format: {}", sql);
                }

                try {
                    long elV = Long.parseLong(els[0]);
                    if (elV <= version) {
                        log.info("Skipping {}", sql);
                        continue;
                    }

                    try (InputStream elIs = PostgreSQLDataStore.class.getResourceAsStream("/store/postgres/schema/" + sql)) {
                        String update = IOUtils.toString(Objects.requireNonNull(elIs), StandardCharsets.UTF_8);
                        stmt.execute(update);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (stmt.executeUpdate("INSERT INTO schema (version) VALUES (" + elV + ")") != 1) {
                        throw new RuntimeException("Could not update schema version");
                    }

                    log.info("Updated schema to version {}", elV);
                } catch (NumberFormatException e) {
                    log.warn("Invalid schema update version: {}", els[0]);
                }
            }

            conn.commit();
        });
    }

    private <R> R withConnFunction(ConnFunction<Connection, R> function) {
        try (Connection conn = pool.get()) {
            conn.setAutoCommit(true);
            return function.run(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void withConnConsumer(ConnConsumer<Connection> consumer) {
        try (Connection conn = pool.get()) {
            consumer.run(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <R> R withStmtFunction(String sql, Connection conn, StmtFunction<PreparedStatement, R> function) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            return function.run(stmt);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <R> R withStmtFunction(String sql, StmtFunction<PreparedStatement, R> function) {
        return withConnFunction(conn -> withStmtFunction(sql, conn, function));
    }

    private void withStmtConsumer(String sql, StmtConsumer<PreparedStatement> c) {
        withConnConsumer(conn -> withStmtConsumer(sql, conn, c));
    }

    private void withStmtConsumer(String sql, Connection conn, StmtConsumer<PreparedStatement> c) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            c.run(stmt);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void withTransaction(Consumer<Connection> c) {
        withConnConsumer(conn -> {
            try {
                conn.setAutoCommit(false);
                c.accept(conn);
                conn.setAutoCommit(true);
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            }
        });
    }

    private <R> R withTransactionFunction(Function<Connection, R> c) {
        return withConnFunction(conn -> {
            try {
                conn.setAutoCommit(false);
                R v = c.apply(conn);
                conn.setAutoCommit(true);
                return v;
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            }
        });
    }

    private long getSchemaVersion() {
        return withStmtFunction("SELECT * FROM schema ORDER BY version DESC LIMIT 1", stmt -> {
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return -1L;
            }

            return rSet.getLong("version");
        });
    }

    private Optional<ChannelDao> findChannel(ResultSet rSet) throws SQLException {
        if (!rSet.next()) {
            return Optional.empty();
        }

        ChannelDao dao = new ChannelDao(rSet.getLong("lid"), ChannelID.fromRaw(rSet.getString("id")));
        return Optional.of(dao);
    }

    @Override
    public List<ChannelDao> listChannels() {
        String sql = "SELECT * FROM channels WHERE network = 'grid'";
        return withStmtFunction(sql, stmt -> {
            List<ChannelDao> channels = new ArrayList<>();

            ResultSet rSet = stmt.executeQuery();
            while (rSet.next()) {
                channels.add(new ChannelDao(rSet.getLong("lid"), ChannelID.fromRaw(rSet.getString("id"))));
            }

            return channels;
        });
    }

    @Override
    public Optional<ChannelDao> findChannel(long cLid) {
        String sql = "SELECT * FROM channels WHERE network = 'grid' AND lid = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cLid);
            return findChannel(stmt.executeQuery());
        });
    }

    @Override
    public Optional<ChannelDao> findChannel(ChannelID cId) {
        String sql = "SELECT * FROM channels WHERE network = 'grid' AND id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, cId.base());
            return findChannel(stmt.executeQuery());
        });
    }

    @Override
    public long addToStream(long eLid) {
        String sql = "INSERT INTO channel_event_stream (lid) VALUES (?) RETURNING sid";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, eLid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new IllegalStateException("Inserted event " + eLid + " in stream but got no SID back");
            }
            return rSet.getLong(1);
        });
    }

    @Override
    public long getStreamPosition() {
        String sql = "SELECT MAX(sid) FROM channel_event_stream";
        return withStmtFunction(sql, stmt -> {
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return 0L;
            }

            return rSet.getLong(1);
        });
    }

    @Override
    public ChannelDao saveChannel(ChannelDao ch) {
        String sql = "INSERT INTO channels (id,network) VALUES (?,'grid') RETURNING lid";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, ch.getId().base());
            ResultSet rSet = stmt.executeQuery();

            if (!rSet.next()) {
                throw new IllegalStateException("Inserted channel " + ch.getId() + " but got no LID back");
            }

            long sid = rSet.getLong(1);
            ChannelID id = ch.getId();
            return new ChannelDao(sid, id);
        });
    }

    private long insertEvent(ChannelEvent ev) {
        String sql = "INSERT INTO channel_events (id,channel_lid,meta,data) VALUES (?,?,?::jsonb,?::jsonb) RETURNING lid";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, ev.getId().base());
            stmt.setLong(2, ev.getChannelSid());
            stmt.setString(3, GsonUtil.toJson(ev.getMeta()));
            stmt.setString(4, GsonUtil.toJson(ev.getData()));
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new IllegalStateException("Inserted channel event but got no LID back");
            }

            return rSet.getLong("lid");
        });
    }

    private void updateEvent(ChannelEvent ev) {
        String sql = "UPDATE channel_events SET meta = ?::jsonb WHERE lid = ?";
        withStmtConsumer(sql, stmt -> {
            stmt.setString(1, GsonUtil.toJson(ev.getMeta()));
            stmt.setLong(2, ev.getLid());
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Channel Event # " + ev.getLid() + ": DB updated " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public ChannelEvent saveEvent(ChannelEvent ev) {
        if (ev.hasLid()) {
            updateEvent(ev);
        } else {
            long sid = insertEvent(ev);
            ev.setLid(sid);
        }

        return ev;
    }

    @Override
    public ChannelEvent getEvent(ChannelID cId, EventID eId) throws IllegalStateException {
        return findEvent(cId, eId).orElseThrow(() -> new ObjectNotFoundException("Event", eId.full()));
    }

    @Override
    public ChannelEvent getEvent(long eSid) {
        return findEvent(eSid).orElseThrow(() -> new ObjectNotFoundException("Event", Long.toString(eSid)));
    }

    @Override
    public EventID getEventId(long eLid) {
        return withStmtFunction("SELECT id FROM channel_events WHERE lid = ?", stmt -> {
            stmt.setLong(1, eLid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("Event", Long.toString(eLid));
            }

            return new EventID(rSet.getString("id"));
        });
    }

    @Override
    public long getEventTid(long cLid, EventID eId) {
        String sql = "SELECT * FROM channel_events e LEFT JOIN channel_event_stream s ON s.eLid = e.lid WHERE e.channel_lid = ? AND e.id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cLid);
            stmt.setString(2, eId.base());
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("Event", eId.full());
            }

            return make(rSet).getSid();
        });
    }

    @Override
    public Optional<Long> findEventLid(ChannelID cId, EventID eId) throws ObjectNotFoundException {
        String sql = "SELECT e.lid FROM channels c JOIN channel_events e ON e.channel_lid = c.sid WHERE c.id = ? AND e.id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, cId.base());
            stmt.setString(2, eId.base());
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(rSet.getLong("lid"));
        });
    }

    @Override
    public List<ChannelEvent> getNext(long lastSid, long amount) {
        String sql = "SELECT * FROM channel_event_stream s JOIN channel_events e ON s.eLid = e.lid WHERE s.sid > ? ORDER BY s.sid ASC LIMIT ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, lastSid);
            stmt.setLong(2, amount);
            ResultSet rSet = stmt.executeQuery();

            List<ChannelEvent> events = new ArrayList<>();
            while (rSet.next()) {
                events.add(make(rSet));
            }
            return events;
        });
    }

    @Override
    public List<ChannelEvent> getTimelineNext(long cLid, long lastTid, long amount) {
        String sql = "SELECT * FROM channel_event_stream s JOIN channel_events e ON s.eLid = e.lid WHERE e.channel_lid = ? AND s.sid > ? ORDER BY s.sid ASC LIMIT ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cLid);
            stmt.setLong(2, lastTid);
            stmt.setLong(3, amount);
            ResultSet rSet = stmt.executeQuery();

            List<ChannelEvent> events = new ArrayList<>();
            while (rSet.next()) {
                events.add(make(rSet));
            }
            return events;
        });
    }

    @Override
    public List<ChannelEvent> getTimelinePrevious(long cLid, long lastTid, long amount) {
        String sql = "SELECT * FROM channel_event_stream s JOIN channel_events e ON s.lid = e.lid WHERE e.channel_lid = ? AND s.sid < ? ORDER BY s.sid DESC LIMIT ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cLid);
            stmt.setLong(2, lastTid);
            stmt.setLong(3, amount);
            ResultSet rSet = stmt.executeQuery();

            List<ChannelEvent> events = new ArrayList<>();
            while (rSet.next()) {
                events.add(make(rSet));
            }
            return events;
        });
    }

    private ChannelEvent make(ResultSet rSet) throws SQLException {
        long cLid = rSet.getLong("channel_lid");
        long sid = rSet.getLong("lid");
        ChannelEventMeta meta = GsonUtil.parse(rSet.getString("meta"), ChannelEventMeta.class);
        ChannelEvent ev = new ChannelEvent(cLid, sid, meta);
        if (ev.getMeta().isPresent()) {
            ev.setData(GsonUtil.parseObj(rSet.getString("data")));
        }
        try {
            ev.setSid(rSet.getLong(rSet.findColumn("sid")));
        } catch (SQLException e) {
            if (log.isTraceEnabled()) {
                log.debug("No column sid");
                log.trace("SID request", e);
            }
        }
        return ev;
    }

    @Override
    public Optional<ChannelEvent> findEvent(ChannelID cId, EventID eId) {
        String sqlChIdToSid = "SELECT sid FROM channels WHERE id = ?";
        String sql = "SELECT * FROM channel_events WHERE id = ? and cSid = (" + sqlChIdToSid + ")";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, eId.base());
            stmt.setString(2, cId.base());
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(make(rSet));
        });
    }

    @Override
    public Optional<ChannelEvent> findEvent(long eLid) {
        return withStmtFunction("SELECT * FROM channel_events WHERE lid = ?", stmt -> {
            stmt.setLong(1, eLid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(make(rSet));
        });
    }

    private void updateExtremities(String type, long cLid, List<Long> toRemove, List<Long> toAdd) {
        withTransaction(conn -> {
            withStmtConsumer("DELETE FROM channel_extremities_" + type + " WHERE event_lid = ?", conn, stmt -> {
                for (long eLid : toRemove) {
                    stmt.setLong(1, eLid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });

            withStmtConsumer("INSERT INTO channel_extremities_" + type + " (channel_lid,event_lid) VALUES (?,?)", conn, stmt -> {
                for (long eLid : toAdd) {
                    stmt.setLong(1, cLid);
                    stmt.setLong(2, eLid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });
        });
    }

    private List<Long> getExtremities(String type, long cLid) {
        return withStmtFunction("SELECT event_lid FROM channel_extremities_" + type + " WHERE channel_lid = ?", stmt -> {
            stmt.setLong(1, cLid);
            ResultSet rSet = stmt.executeQuery();

            List<Long> extremities = new ArrayList<>();
            while (rSet.next()) {
                extremities.add(rSet.getLong("event_lid"));
            }
            return extremities;
        });
    }

    @Override
    public void updateBackwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd) {
        updateExtremities("backward", cLid, toRemove, toAdd);
    }

    @Override
    public List<Long> getBackwardExtremities(long cLid) {
        return getExtremities("backward", cLid);
    }

    @Override
    public void updateForwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd) {
        updateExtremities("forward", cLid, toRemove, toAdd);
    }

    @Override
    public List<Long> getForwardExtremities(long cLid) {
        return getExtremities("forward", cLid);
    }

    @Override
    public long insertIfNew(long cLid, ChannelState state) {
        if (Objects.nonNull(state.getSid())) {
            return state.getSid();
        }

        String sql = "INSERT INTO channel_states (channel_lid) VALUES (?) RETURNING lid";
        String evSql = "INSERT INTO channel_state_data (state_lid,event_lid) VALUES (?,?)";

        return withTransactionFunction(conn -> {
            long sSid = withStmtFunction(sql, conn, stmt -> {
                stmt.setLong(1, cLid);
                ResultSet rSet = stmt.executeQuery();
                if (!rSet.next()) {
                    throw new IllegalStateException("Inserted state for channel " + cLid + " but got no LID back");
                }

                return rSet.getLong("lid");
            });

            withStmtConsumer(evSql, conn, stmt -> {
                for (long eSid : state.getEvents().stream().map(ChannelEvent::getLid).collect(Collectors.toList())) {
                    stmt.setLong(1, sSid);
                    stmt.setLong(2, eSid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });

            return sSid;
        });
    }

    @Override
    public ChannelState getState(long sid) {
        return withConnFunction(conn -> {
            String evSql = "SELECT e.* from channel_state_data s LEFT JOIN channel_events e ON e.lid = s.event_lid WHERE s.state_lid = ?";
            List<ChannelEvent> events = withStmtFunction(evSql, conn, stmt -> {
                List<ChannelEvent> list = new ArrayList<>();
                stmt.setLong(1, sid);
                ResultSet rSet = stmt.executeQuery();
                while (rSet.next()) {
                    list.add(make(rSet));
                }
                return list;
            });

            return new ChannelState(sid, events);
        });
    }

    @Override
    public void map(long evLid, long stateSid) {
        withStmtConsumer("INSERT INTO channel_event_states (event_lid,state_lid) VALUES (?,?)", stmt -> {
            stmt.setLong(1, evLid);
            stmt.setLong(2, stateSid);
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Channel Event " + evLid + " state: DB inserted " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public ChannelState getStateForEvent(long eLid) {
        String sql = "SELECT state_lid FROM channel_event_states WHERE event_lid = ?";
        return getState(withStmtFunction(sql, stmt -> {
            stmt.setLong(1, eLid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new IllegalArgumentException("No state for Channel event " + eLid);
            }

            return rSet.getLong("state_lid");
        }));
    }

    @Override
    public boolean hasUsername(String username) {
        return withStmtFunction("SELECT * FROM identity_users WHERE id = ? LIMIT 1", stmt -> {
            stmt.setString(1, username);
            return stmt.executeQuery().next();
        });
    }

    @Override
    public long getUserCount() {
        return withStmtFunction("SELECT COUNT(*) as total FROM identity_users", stmt -> {
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new IllegalStateException("Expected one row for count, but got none");
            }

            return rSet.getLong("total");
        });
    }

    @Override
    public long addUser(String id) {
        String sql = "INSERT INTO identity_users (id) VALUES (?) RETURNING lid";

        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, id);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new IllegalStateException("Inserted user " + id + " but got no LID back");
            }

            return rSet.getLong("lid");
        });
    }

    @Override
    public void addCredentials(long userLid, Credentials credentials) {
        SecureCredentials secCreds = SecureCredentials.from(credentials);

        String sql = "INSERT INTO identity_user_credentials (user_lid, type, data) VALUES (?,?,?)";
        withStmtConsumer(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, secCreds.getType());
            stmt.setString(3, secCreds.getData());
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("User " + userLid + " credentials state: DB inserted " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public SecureCredentials getCredentials(long userLid, String type) {
        String sql = "SELECT * FROM identity_user_credentials WHERE user_lid = ? and type = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, type);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("Credentials of type " + type + " for user LID " + userLid);
            }

            return new SecureCredentials(rSet.getString("type"), rSet.getString("data"));
        });
    }

    @Override
    public Optional<UserDao> findUser(long lid) {
        return withStmtFunction("SELECT * FROM identity_users WHERE lid = ?", stmt -> {
            stmt.setLong(1, lid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            UserDao dao = new UserDao();
            dao.setLid(rSet.getLong("lid"));
            dao.setId(rSet.getString("id"));

            return Optional.of(dao);
        });
    }

    @Override
    public Optional<UserDao> findUser(String id) {
        return withStmtFunction("SELECT * FROM identity_users WHERE id = ?", stmt -> {
            stmt.setString(1, id);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            UserDao dao = new UserDao();
            dao.setLid(rSet.getLong("lid"));
            dao.setId(rSet.getString("id"));

            return Optional.of(dao);
        });
    }

    @Override
    public Optional<UserDao> findUserByStoreLink(ThreePid storeId) {
        String sql = "SELECT user_lid FROM identity_user_store_links WHERE type = ? AND id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, storeId.getMedium());
            stmt.setString(2, storeId.getAddress());
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return findUser(rSet.getLong("user_lid"));
        });
    }

    @Override
    public Optional<UserDao> findUserByTreePid(ThreePid tpid) {
        return Optional.empty();
    }

    @Override
    public boolean hasUserAccessToken(String token) {
        return withStmtFunction("SELECT * FROM user_access_tokens WHERE token = ?", stmt -> {
            stmt.setString(1, token);
            ResultSet rSet = stmt.executeQuery();
            return rSet.next();
        });
    }

    @Override
    public void insertUserAccessToken(long uLid, String token) {
        withStmtConsumer("INSERT INTO user_access_tokens (ulid, token) VALUES (?,?)", stmt -> {
            stmt.setLong(1, uLid);
            stmt.setString(2, token);
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new IllegalStateException("User Access token insert: DB inserted " + rc + " row(s). 1 expected");
            }
        });
    }

    @Override
    public void deleteUserAccessToken(String token) {
        withStmtConsumer("DELETE FROM user_access_tokens WHERE token = ?", stmt -> {
            stmt.setString(1, token);
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new ObjectNotFoundException("User Access Token", "<REDACTED>");
            }
        });
    }

    @Override
    public Optional<ChannelID> lookupChannelAlias(String chAlias) {
        return withStmtFunction("SELECT * FROM channel_aliases WHERE channel_alias = ?", stmt -> {
            stmt.setString(1, chAlias);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(ChannelID.fromRaw(rSet.getString("channel_id")));
        });
    }

    @Override
    public Set<String> findChannelAlias(ServerID srvId, ChannelID cId) {
        return withStmtFunction("SELECT * FROM channel_aliases WHERE server_id = ? AND channel_id = ?", stmt -> {
            Set<String> list = new HashSet<>();
            stmt.setString(1, srvId.base());
            stmt.setString(2, cId.base());
            ResultSet rSet = stmt.executeQuery();
            while (rSet.next()) {
                list.add(rSet.getString("channel_alias"));
            }
            return list;
        });
    }

    @Override
    public void setAliases(ServerID origin, ChannelID cId, Set<String> chAliases) {
        withTransaction(conn -> {
            withStmtConsumer("DELETE FROM channel_aliases WHERE server_id = ?", stmt -> {
                stmt.setString(1, origin.base());
                stmt.executeUpdate();
            });
            withStmtConsumer("INSERT INTO channel_aliases (channel_id,channel_alias,server_id,auto) VALUES (?,?,?, true)", stmt -> {
                for (String cAlias : chAliases) {
                    stmt.setString(1, cId.base());
                    stmt.setString(2, cAlias);
                    stmt.setString(3, origin.base());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });
        });
    }

    @Override
    public void unmap(String chAd) {
        withStmtConsumer("DELETE FROM channel_aliases WHERE channel_alias = ?", stmt -> {
            stmt.setString(1, chAd);
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new IllegalStateException("Channel Alias to ID mapping: DB deleted " + rc + " rows. >= 1 expected");
            }
        });
    }

    @Override
    public void linkUserToStore(long userLid, ThreePid storeId) {
        String sql = "INSERT INTO identity_user_store_links (user_lid, type, id) VALUES (?,?,?)";
        withStmtConsumer(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, storeId.getMedium());
            stmt.setString(3, storeId.getAddress());
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new IllegalStateException("User LID to Store ID insert: DB inserted " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public Set<ThreePid> listThreePid(long userLid) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Set<ThreePid> listThreePid(long userLid, String medium) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void addThreePid(long userLid, ThreePid tpid) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void removeThreePid(long userLid, ThreePid tpid) {
        throw new RuntimeException("Not implemented");
    }

}
