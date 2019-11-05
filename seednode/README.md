# Bisq Seed Node

* Install bisq-seednode.service in /etc/systemd/system
* Install bisq-seednode in /etc/default
* Modify the executable paths and configuration as necessary
* Then you can do:

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
