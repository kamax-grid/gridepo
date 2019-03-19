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
import io.kamax.grid.gridepo.core.channel.ChannelDao;
import io.kamax.grid.gridepo.core.channel.event.ChannelEvent;
import io.kamax.grid.gridepo.core.channel.state.ChannelState;
import io.kamax.grid.gridepo.core.store.SqlConnectionPool;
import io.kamax.grid.gridepo.core.store.Store;
import io.kamax.grid.gridepo.exception.NotImplementedException;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class PostgreSQLStore implements Store {

    private static final Logger log = LoggerFactory.getLogger(PostgreSQLStore.class);

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

    public PostgreSQLStore(StorageConfig cfg) {
        this.pool = new SqlConnectionPool(cfg);
        withConnConsumer(conn -> conn.isValid(1000));
        log.info("Connected");
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

    @Override
    public Optional<ChannelDao> findChannel(long cSid) {
        String sql = "SELECT * FROM channels WHERE network = 'grid' AND sid = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cSid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            ChannelDao dao = new ChannelDao(rSet.getLong("sid"), ChannelID.fromRaw(rSet.getString("id")));
            return Optional.of(dao);
        });
    }

    @Override
    public ChannelDao saveChannel(ChannelDao ch) {
        String sql = "INSERT INTO channels (id, network) VALUES (?, 'grid') RETURNING sid";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, ch.getId().base());
            ResultSet rSet = stmt.executeQuery();

            long sid = rSet.getLong(1);
            ChannelID id = ch.getId();
            return new ChannelDao(sid, id);
        });
    }

    private long insertEvent(ChannelEvent ev) {
        String sql = "INSERT INTO channel_events (id, cSid, properties, data) VALUES (?, ?, ?::jsonb, ?::jsonb) RETURNING sid";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, ev.getId().base());
            stmt.setLong(2, ev.getChannelSid());
            stmt.setObject(3, ev.getMeta(), Types.OTHER);
            stmt.setString(4, GsonUtil.toJson(ev.getData()));
            return stmt.executeQuery().getLong(1);
        });
    }

    private void updateEvent(ChannelEvent ev) {
        String sql = "UPDATE channel_events SET properties = ?::jsonb WHERE sid = ?";
        withStmtConsumer(sql, stmt -> {
            stmt.setObject(1, ev.getMeta(), Types.OTHER);
            stmt.setLong(2, ev.getSid());
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Channel Event # " + ev.getSid() + ": DB updated " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public ChannelEvent saveEvent(ChannelEvent ev) {
        if (ev.hasSid()) {
            updateEvent(ev);
        } else {
            long sid = insertEvent(ev);
            ev.setSid(sid);
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
    public EventID getEventId(long eSid) {
        return withStmtFunction("SELECT id FROM channel_events WHERE sid = ?", stmt -> {
            stmt.setLong(1, eSid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("Event", Long.toString(eSid));
            }

            return new EventID(rSet.getString("id"));
        });
    }

    @Override
    public long getEventSid(ChannelID cId, EventID eId) throws ObjectNotFoundException {
        String sql = "SELECT e.sid FROM channels c JOIN channel_events e ON e.cSid = c.sid WHERE c.id = ? AND e.id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, cId.base());
            stmt.setString(2, eId.base());
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("Event ", cId.full() + "/" + eId.full());
            }

            return rSet.getLong("sid");
        });
    }

    @Override
    public List<ChannelEvent> getNext(long lastSid, long amount) {
        String sql = "SELECT * FROM channel_events WHERE sid > ? ORDER BY sid ASC LIMIT ?";
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

    private ChannelEvent make(ResultSet rSet) throws SQLException {
        long cSid = rSet.getLong("cSid");
        long sid = rSet.getLong("sid");
        ChannelEventMeta meta = GsonUtil.parse(rSet.getString("meta"), ChannelEventMeta.class);
        ChannelEvent ev = new ChannelEvent(cSid, sid, meta);
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
    public Optional<ChannelEvent> findEvent(long eSid) {
        return withStmtFunction("SELECT * FROM channel_events WHERE sid = ?", stmt -> {
            stmt.setLong(1, eSid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(make(rSet));
        });
    }

    @Override
    public void updateExtremities(long cSid, List<Long> toRemove, List<Long> toAdd) {
        withConnConsumer(conn -> {
            conn.setAutoCommit(false);

            withStmtConsumer("DELETE FROM channel_extremities WHERE eSid = ?", conn, stmt -> {
                for (long eSid : toRemove) {
                    stmt.setLong(1, eSid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });

            withStmtConsumer("INSERT INTO channel_extremities (cSid, eSid) VALUES (?,?)", conn, stmt -> {
                for (long eSid : toAdd) {
                    stmt.setLong(1, cSid);
                    stmt.setLong(2, eSid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });

            conn.commit();
        });
    }

    @Override
    public List<Long> getExtremities(long cSid) {
        return withStmtFunction("SELECT eSid FROM channel_extremities WHERE cSid = ?", stmt -> {
            stmt.setLong(1, cSid);
            ResultSet rSet = stmt.executeQuery();

            List<Long> extremities = new ArrayList<>();
            while (rSet.next()) {
                extremities.add(rSet.getLong("eSid"));
            }
            return extremities;
        });
    }

    @Override
    public long insertIfNew(long cSid, ChannelState state) {
        if (Objects.nonNull(state.getSid())) {
            return state.getSid();
        }

        String sql = "INSERT INTO channel_states (cSid) VALUES (?) RETURNING sid";
        String evSql = "INSERT INTO channel_state_data (sSid, eSid) VALUES (?,?)";

        return withConnFunction(conn -> {
            conn.setAutoCommit(false);

            long sSid = withStmtFunction(sql, conn, stmt -> {
                stmt.setLong(1, cSid);
                ResultSet rSet = stmt.executeQuery();
                if (!rSet.next()) {
                    throw new IllegalStateException("Inserted state for channel " + cSid + " but got no SID back");
                }

                return rSet.getLong("sid");
            });

            withStmtConsumer(evSql, conn, stmt -> {
                for (long eSid : state.getEvents().stream().map(ChannelEvent::getSid).collect(Collectors.toList())) {
                    stmt.setLong(1, cSid);
                    stmt.setLong(2, eSid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });

            conn.commit();

            return sSid;
        });
    }

    @Override
    public ChannelState getState(long sid) {
        throw new NotImplementedException();
    }

    @Override
    public void map(long evSid, long stateSid) {
        throw new NotImplementedException();
    }

    @Override
    public ChannelState getStateForEvent(long evSid) {
        throw new NotImplementedException();
    }

    @Override
    public boolean hasUser(String username) {
        return false;
    }

    @Override
    public void storeUser(String username, String password) {
        throw new NotImplementedException();
    }

    @Override
    public Optional<String> findPassword(String username) {
        throw new NotImplementedException();
    }

    @Override
    public Optional<ChannelID> findChannelIdForAddress(String chAd) {
        throw new NotImplementedException();
    }

    @Override
    public List<String> findChannelAddressForId(ChannelID cId) {
        throw new NotImplementedException();
    }

    @Override
    public void map(ChannelID cId, String chAd) {
        throw new NotImplementedException();
    }

    @Override
    public void unmap(String chAd) {
        throw new NotImplementedException();
    }

}
