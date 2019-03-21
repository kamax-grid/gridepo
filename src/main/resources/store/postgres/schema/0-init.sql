CREATE TABLE channels
(
    sid bigserial NOT NULL,
    id text NOT NULL,
    network text NOT NULL,
    CONSTRAINT c_ids UNIQUE (id, network)
);
CREATE INDEX c_id_idx
    ON channels USING hash(id);
CREATE INDEX c_net_idx
    ON channels USING hash(network);

CREATE TABLE channel_events
(
    sid bigserial NOT NULL,
    cSid bigint NOT NULL,
    id text NOT NULL,
    meta jsonb NOT NULL,
    data jsonb,
    CONSTRAINT c_ev_gid UNIQUE(id,cSid)
);
CREATE INDEX c_ev_id_idx
    ON channel_events USING hash(id);
CREATE INDEX c_ev_cid_idx
    ON channel_events USING hash(cSid);

CREATE TABLE channel_states
(
    sid bigserial NOT NULL,
    cSid bigint NOT NULL
);

CREATE TABLE channel_state_data
(
    sSid bigint NOT NULL,
    eSid bigint NOT NULL
);

CREATE TABLE channel_event_states
(
    eSid bigint NOT NULL,
    sSid bigint NOT NULL
);

CREATE TABLE channel_extremities
(
    cSid bigint NOT NULL,
    eSid bigint NOT NULL
);

CREATE TABLE channel_addresses
(
    cAlias text NOT NULL,
    cId text NOT NULL,
    auto boolean NOT NULL,
    CONSTRAINT c_adr_alias UNIQUE(cAlias)
);

CREATE TABLE users
(
    sid bigserial NOT NULL,
    username text NOT NULL,
    password text,
    CONSTRAINT u_username UNIQUE (username)
);
CREATE INDEX u_username_idx
    ON users USING hash(username);
