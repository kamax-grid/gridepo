# Database
## PostgreSQL
### Setup
On debian, after having installed the PostgreSQL server:
```bash
su - postgres
createuser --pwprompt gridepo
psql
```
At the SQL prompt:
```sql
CREATE DATABASE "gridepo"
 ENCODING 'UTF8'
 LC_COLLATE='C'
 LC_CTYPE='C'
 template=template0
 OWNER gridepo;
```
And quit the prompt with:
```
\q
```

Go back in into the Database you created using the password provided above:
```
psql -h localhost --user gridepo gridepo
```
and create the initial schema:
```
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
    lid bigserial NOT NULL,
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
    eLid bigint NOT NULL
);

CREATE TABLE channel_event_states
(
    eLid bigint NOT NULL,
    sSid bigint NOT NULL
);

CREATE TABLE channel_event_stream
(
    sid bigserial NOT NULL,
    eLid bigint NOT NULL
);

CREATE TABLE channel_extremities
(
    cSid bigint NOT NULL,
    eLid bigint NOT NULL
);

CREATE TABLE channel_addresses
(
    cAlias text NOT NULL,
    cId text NOT NULL,
    srvId text NOT NULL,
    auto boolean NOT NULL,
    CONSTRAINT c_adr_alias UNIQUE(cAlias)
);

CREATE TABLE users
(
    lid bigserial NOT NULL,
    username text NOT NULL,
    password text,
    CONSTRAINT u_username UNIQUE (username)
);
CREATE INDEX u_lid_idx
    ON users USING hash(lid);
CREATE INDEX u_username_idx
    ON users USING hash(username);

CREATE TABLE user_access_tokens
(
    uLid bigint NOT NULL,
    token text NOT NULL,
    CONSTRAINT u_token UNIQUE (token)
);
```
And once again, quit with `\q`

In your `gridepo.yaml` config file (if needed, so your install instruction), assuming a local DB:
```yaml
storage:
  database:
    type: 'postgresql'
    connection: '//localhost/gridepo?user=gridepo&password=gridepo'
```
Adapt the password with what was provided to the `createuser` command
