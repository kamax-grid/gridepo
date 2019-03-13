CREATE TABLE channels
(
    sid bigserial NOT NULL,
    id text NOT NULL,
    network text NOT NULL,
    CONSTRAINT c_ids UNIQUE (id, network)
);
CREATE INDEX c_id
    ON channels USING hash(id);
CREATE INDEX c_net
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
CREATE INDEX c_ev_id
    ON channel_events USING hash(id);
CREATE INDEX c_ev_cid
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