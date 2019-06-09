CREATE TABLE IF NOT EXISTS channels
(
    lid bigserial NOT NULL,
    id text NOT NULL,
    network text NOT NULL,
    CONSTRAINT c_ids UNIQUE (id, network)
);
CREATE INDEX IF NOT EXISTS c_id_idx
    ON channels USING hash(id);
CREATE INDEX IF NOT EXISTS c_net_idx
    ON channels USING hash(network);

CREATE TABLE IF NOT EXISTS channel_events
(
    lid bigserial NOT NULL,
    channel_lid bigint NOT NULL,
    id text NOT NULL,
    meta jsonb NOT NULL,
    data jsonb,
    CONSTRAINT c_ev_gid UNIQUE(id,channel_lid)
);
CREATE INDEX IF NOT EXISTS c_ev_id_idx
    ON channel_events USING hash(id);
CREATE INDEX IF NOT EXISTS c_ev_cid_idx
    ON channel_events USING hash(channel_lid);

CREATE TABLE IF NOT EXISTS channel_states
(
    lid bigserial NOT NULL,
    channel_lid bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS channel_state_data
(
    state_lid bigint NOT NULL,
    event_lid bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS channel_event_states
(
    event_lid bigint NOT NULL,
    state_lid bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS channel_event_stream
(
    sid bigserial NOT NULL,
    lid bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS channel_extremities_backward
(
    channel_lid bigint NOT NULL,
    event_lid bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS channel_extremities_forward
(
    channel_lid bigint NOT NULL,
    event_lid bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS channel_aliases
(
    channel_alias text NOT NULL,
    channel_id text NOT NULL,
    server_id text NOT NULL,
    auto boolean NOT NULL,
    CONSTRAINT c_adr_alias UNIQUE(channel_alias)
);

CREATE TABLE IF NOT EXISTS users
(
    lid bigserial NOT NULL,
    username text NOT NULL,
    password text,
    CONSTRAINT u_username UNIQUE (username)
);
CREATE INDEX IF NOT EXISTS u_lid_idx
    ON users USING hash(lid);
CREATE INDEX IF NOT EXISTS u_username_idx
    ON users USING hash(username);

CREATE TABLE IF NOT EXISTS user_access_tokens
(
    user_lid bigint NOT NULL,
    token text NOT NULL,
    CONSTRAINT u_token UNIQUE (token)
);
