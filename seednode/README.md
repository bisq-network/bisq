# Bisq Seed Node

The distribution ships with a systemd .desktop file. Validate/change the executable/config paths within the shipped `bisq-seednode.service` file and copy/move the file to your systemd directory (something along `/usr/lib/systemd/system/`). Now you can control your *Seed Node* via the usual systemd start/stop commands

```
systemctl start bisq-seednode.service
systemctl stop bisq-seednode.service
```
and
```
systemctl enable bisq-seednode.service
systemctl disable bisq-seednode.service
```

Follow the logs created by the service by inspecting

```
journalctl --unit bisq-seednode --follow
```
