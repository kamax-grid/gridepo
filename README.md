# Gridepo
Implementation of a [Grid](https://gitlab.com/thegridprotocol/home) Data Server with basic support for [Matrix C2S API](https://matrix.org/docs/spec/client_server/r0.4.0.html).

## Status
Work in Progress towards the first usable version as `v0.1`

## Build
```bash
./gradlew build
```

### Tests
#### PostgreSQL integration
Via env variable, adapt as needed:
```bash
export GRIDEPO_TEST_STORE_POSTGRESQL_CONFIG='{"type":"postgresql","connection":"//localhost/grid?user=grid&password=grid"}'
./gradlew test
```

## Community
### On Grid
```bash
$ cd grid
$ ./bootstrap
Building The Grid, this shouldn't take long...
```

We are currently bootstrapping. Check again later!

### Matrix
Use any of the following room aliases to join the project room:
  - #gridepo:kamax.io
  - #kamax-gridepo:t2bot.io
