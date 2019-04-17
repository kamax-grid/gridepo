# Getting started
1. [Preparation](#preparation)
2. [Install](#install)
3. [Database](#database)
4. [Configure](#configure)
5. [Integrate](#integrate)
6. [Validate](#validate)
7. [Next steps](#next-steps)

Following these quick start instructions, you will have a basic working setup to federate with The Grid network.

If possible, we highly recommend using the [Docker Compose installation method](install/docker.md#docker-compose) which will provide with a working server
and database, only leaving the reverse proxy part to be done.
---

## Preparation
Minimum requirements:
- DNS domain, sub-domain dedicated to the API strongly recommended
- Valid CA certificate, Let's Encrypt recommended

If you do not use Docker, you will also need Java 8 on the host running Gridepo

The following guide will assume you will use a sub-domain and use `grid.example.org` in the configuration snippets.
Change this value to your liking. If you cannot use a sub-domain, an example of how to use `example.org` is available at
the end of this guide.

## Install
Install via:
- [Docker image](install/docker.md)
- [Debian package](install/debian.md)
- [Sources](install/build.md)

See the [Latest release](https://github.com/kamax-matrix/mxisd/releases/latest) for links to each.

## Database
Follow [these steps](database.md).

## Configure
> **NOTE**: Please view the install instruction for your platform, as this step might be optional or already handled for you.

If you haven't created a configuration file yet, copy `gridepo.sample.yaml` to where the configuration file is stored given
your installation method and edit to your needs.

The following items must be at least configured:
- `domain`: Will represent the DNS/IP domain used to identify your server and build various IDs.
- `storage.data`: File storage location for Gridepo, including cryptographic files (signing keys, etc.).
- `storage.database.connection`: JDBC URL pointing to your PostgreSQL database.

**NOTE:** The Grid protocol will separate IDs from domains/addresses, unlike Matrix. To ease the transition, Gridepo
will use the provided domain (either DNS or IP, with or without port) to auto-discover and connect.

You can either set the direct, final destination or use [Well-known discovery](federation.md#discovery) if you want to
keep familiar IDs and Aliases or use an arbitrary path for gridepo, like `/gridepo/` to keep it all contained.

## Integrate
Gridepo provides two set of APIs:
- Grid data federation endpoints, under `/data/`
- Matrix Client endpoint, under `/_matrix/client/`

Both will need to be passed to Gridepo. If you are already using Matrix, we recommend using a dedicated sub-domain as to
not interfere with your current installation.

### Reverse proxy
#### nginx
Typical configuration would look like:
```nginx
server {
    listen 443 ssl;
    server_name grid.example.org;

    # ...

    location / {
        proxy_pass http://localhost:9009;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
}
```

If you decided to use the [Well-known discovery](federation.md#discovery) and want to host it all under your main domain
and a custom path, per example `https://example.org/_grid/`, the following configuration can be used:
```nginx
server {
    listen 443 ssl;
    # More listen statements

    server_name example.org;

    # ...

    location /gridepo/ {
        proxy_pass http://localhost:9009;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
}
```
with the following well-known located at `https://example.org/.well-known/grid`:
```json
{
    "data": {
        "server": "https://example.org/gridepo"
    }
}
```

## Validate
Point your matrix client at `https://grid.example.org/` and register the first account which will be considered as an
admin account. If you would like to register more than one account, you will need to specifically enable registration
in the configuration.

## Next steps
Come say Hi and join #test:gridify.org, our current landing test channel.
