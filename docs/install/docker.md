# Docker
## Standalone image
Repository is at [Docker Hub](https://hub.docker.com/r/kamax/gridepo/tags).
### Fetch
Pull the latest stable image:
```bash
docker pull kamax/gridepo
```

### Configure
On first run, simply using `GRID_DOMAIN` as an environment variable will create a default config for you.
You can also provide a configuration file named `gridepo.yaml` in the volume mapped to `/etc/gridepo` before starting your
container using the [sample configuration file](../../gridepo.sample.yaml).

### Run
Use the following command after adapting to your needs:
- The `GRID_DOMAIN` environment variable to yours
- The volumes host paths

```bash
docker run --rm -e GRID_DOMAIN=example.org -v /data/gridepo/etc:/etc/gridepo -v /data/gridepo/var:/var/gridepo -p 9009:9009 -t kamax/gridepo
```

For more info, including the list of possible tags, see [the public repository](https://hub.docker.com/r/kamax/gridepo/)

## Docker-compose
Use the following definition:
```yaml
version: '2'

volumes:
  gridepo-etc:
  gridepo-var:
  db:
services:
  db:
    image: 'kamax/grid-postgres:latest'
    restart: always
    volumes:
      - db:/var/lib/postgresql/data
  gridepo:
    image: 'kamax/gridepo:latest'
    restart: always
    depends_on:
      - 'db'
    volumes:
      - gridepo-etc:/etc/gridepo
      - gridepo-var:/var/gridepo
    ports:
      - 9009:9009
    environment:
      - GRID_DOMAIN=
```
Set the `GRID_DOMAIN` environment variable in the Gridepo container.

You can then start the stack with the usual command:
```bash
docker-compose up
```

## Next steps
If you were, go back to the [Getting Started](../getting-started.md#reverse-proxy) and continue with Reverse proxy integration.
