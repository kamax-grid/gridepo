# Federation
Federation is the mechanism by which servers exchange data between themselves when clients send events within a channel.

## Discovery
### Overview
Discovery is a 3 steps process:
- Attempt to extract a valid DNS domain/IP from an ID
- If successful, try to fetch `https://<domain>/.well-known/grid` and parse an URL to connect to
- Connect to the given URL or the initial extracted DNS domain/IP

### Well-known
Format is:
```json
{
    "data": {
        "server": "<URL without final slash where Gridepo is located>"
    }
}
```
