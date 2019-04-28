# Configuration
This document describe all configuration options.

## Network
### Listeners
You can configure HTTP(S) listeners under the `listeners` key, expecting an array of listener configuration.

Each listener provides the following options:
- `address`: IP to bind to. Use `0.0.0.0` to bind on all interfaces.
- `port`: Port to bind to. In an empty configuration, Gridepo uses `9009`.
- `tls`: If the listener should use TLS. `false` by default.
- `key`: Path to the TLS private key.
- `cert`: Path to the TLS certificate, including any intermediary certificates. This is referred to as "full chain".
- `network`: Array of Network definition.

#### Networks
> *TBC*

### Example
```yaml
listeners:
  - port: 9009
  - port: 19009
    tls: true
    key: '/etc/letsencrypt/live/grid.example.org/privkey.pem'
    cert: '/etc/letsencrypt/live/grid.example.org/fullchain.pem'
```

The above configuration will create two listeners:
- One on port 9009, bound to all IP addresses, using HTTP and supporting all protocols/roles available in Gridepo.
- One on port 19009, bound to all IP addresses, using HTTPS with Let's Encrypt certificate and supporting all
  protocols/roles available in Gridepo.

**NOTE:** As Grid's default network port is the HTTPS port `443`, you will need to use a high port if you want Gridepo
to serve HTTPS directly and not run Gridepo as root. Use the [Well-known](federation.md#well-known) discovery method to
specify whatever port. Please note that **RUNNING GRIDEPO AS ROOT WILL NEVER BE SUPPORTED**.

With the above example, your Well-known file would be:
```json
{
  "data": {
    "server": "https://grid.example.org:19009"
  }
}
```
