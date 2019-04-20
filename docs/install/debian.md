# Debian package
## Requirements
- Any distribution that supports Java 8

## Install
1. Download the [latest release](https://kamax.io/grid/gridepo/?C=M;O=D) at the top
2. Run:
```bash
dpkg -i /path/to/downloaded/gridepo.deb
```
## Files
| Location                              | Purpose                                        |
|---------------------------------------|------------------------------------------------|
| `/etc/gridepo`                        | Configuration directory                        |
| `/etc/gridepo/gridepo.yaml`           | Main configuration file                        |
| `/etc/systemd/system/gridepo.service` | Systemd configuration file for gridepo service |
| `/usr/lib/gridepo`                    | Binaries                                       |
| `/var/lib/gridepo`                    | Default data location                          |

## Control
Start Gridepo using:
```bash
sudo systemctl start gridepo
```

Stop Gridepo using:
```bash
sudo systemctl stop gridepo
```

## Troubleshoot
All logs are sent to `STDOUT` which are saved in `/var/log/syslog` by default.
You can:
- grep & tail using `gridepo`:
```
tail -n 99 -f /var/log/syslog | grep gridepo
```
- use Systemd's journal:
```
journalctl -f -n 99 -u gridepo
```
