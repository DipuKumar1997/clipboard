# AirMesh

AirMesh is a lightweight Linux utility for clipboard synchronization and peer-to-peer file transfers over a local Wi-Fi network.

## Features

- TCP clipboard/file transfer on **port 8889**
- Clipboard synchronization with anti-echo protection
- Systemd user-service integration
- 
## Architecture

- `Main.java` — Application entry point and `--minimized` argument.
- `DiscoveryService.java` — UDP discovery using port `8888`.
- `NetworkServer` / `NetworkClient` — TCP communication using port `8889`.
- `ClipboardService.java` — System clipboard monitoring and synchronization.
- `wifi-sync-app.service` — Systemd background service.

## Requirements

make sure maven and java is installed

#All Installation commands 

```bash
chmod +x install.sh
./install.sh
sudo ufw allow 8888/udp
sudo ufw allow 8889/tcp
sudo ufw reload

systemctl --user enable --now wifi-sync-app.service

systemctl --user status wifi-sync-app.service
systemctl --user daemon-reload
systemctl --user restart wifi-sync-app.service
systemctl --user enable wifi-sync-app.service
systemctl --user is-enabled wifi-sync-app.service
systemctl --user restart wifi-sync-app.service
systemctl --user start wifi-sync-app.service
gnome-extensions enable ubuntu-appindicators@ubuntu.com 2>/dev/null || \
gnome-extensions enable appindicatorsupport@rgcjonas.gmail.com 2>/dev/null  
journalctl --user -u wifi-sync-app.service -n 100 --no-pager
systemctl --user is-active wifi-sync-app.service
journalctl --user -u wifi-sync-app.service -n 100
journalctl --user -u wifi-sync-app.service -f
journalctl --user -u wifi-sync-app.service
systemctl --user cat wifi-sync-app.service
cat ~/.config/systemd/user/wifi-sync-app.service
systemd-analyze --user verify ~/.config/systemd/user/wifi-sync-app.service
sudo ss -lunp | grep 8888
sudo ss -ltnp | grep 8889
UNCONN 0 0 0.0.0.0:8888 0.0.0.0:*

systemctl --user import-environment \
DISPLAY \
WAYLAND_DISPLAY \
XAUTHORITY \
DBUS_SESSION_BUS_ADDRESS \
XDG_RUNTIME_DIR
```

## GNOME Tray Fix

```bash
wifi-sync-app  
wifi-sync-app --minimized
~/.local/bin/wifi-sync-app --minimized
systemctl --user list-unit-files | grep wifi-sync-app
systemctl --user status wifi-sync-app.service
journalctl --user -u wifi-sync-app.service -n 100 --no-pager
pgrep -a java
pgrep -af wifi-sync-app
ls -l ~/.local/share/wifi-sync-app/
ls -l ~/.local/bin/wifi-sync-app
ls -l ~/.config/systemd/user/wifi-sync-app.service
systemctl --user daemon-reload
systemctl --user restart wifi-sync-app.service
systemctl --user status wifi-sync-app.service
journalctl --user -u wifi-sync-app.service -n 100 --no-pager
```


The installer creates:

```text
JAR:       ~/.local/share/wifi-sync-app/
Launcher:  ~/.local/bin/wifi-sync-app
Service:   ~/.config/systemd/user/wifi-sync-app.service
```

Both devices must:

- Be connected to the same Wi-Fi/LAN.
- Not use Wi-Fi client isolation.
- Not have a VPN blocking local traffic.

## Network Architecture

```text
                    Local Wi-Fi / LAN
                           |
             +-------------+-------------+
             |                           |
             v                           v
       +-----------+               +-----------+
       |  Device A |               |  Device B |
       |  AirMesh  |               |  AirMesh  |
       +-----------+               +-----------+
             |                           |
             |<---- UDP 8888 ---------->|
             |     Discovery            |
             |                           |
             |<---- TCP 8889 ---------->|
             | Clipboard / Files        |
             |                           |
             +---------------------------+
```

### UDP Port 8888

Used for:

- Device discovery
- UDP broadcast
- Discovery requests
- Discovery responses

### TCP Port 8889

Used for:

- Clipboard synchronization
- File transfers
- Incoming connections
- Peer-to-peer communication

## Security

AirMesh is designed for trusted local networks.

- Do not expose port `8889` to the public internet.
- Do not forward ports `8888` or `8889` on your router.
- Avoid using AirMesh on untrusted public Wi-Fi.
- Incoming file transfers should require user confirmation.
- Use firewall rules to limit access where appropriate.

## Author

**Dipu Kumar**

A lightweight Linux utility for local-network clipboard synchronization and peer-to-peer file transfers.
