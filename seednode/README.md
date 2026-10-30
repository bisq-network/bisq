# Bisq Seed Node

## Hardware

Highly recommended to use SSD! Minimum specs:

* CPU: 4 cores
* RAM: 8 GB
* SSD: 512 GB (HDD is too slow)

## Software

The following OS's are known to work well:

* Ubuntu 22.04
* Ubuntu 24.04

### Installation

Start with a clean Ubuntu server installation, and run the script
```bash
curl -s https://raw.githubusercontent.com/bisq-network/bisq/master/seednode/install_seednode_debian.sh | sudo bash
```

This will install and configure Bitcoin and Bisq-Seednode services to start on boot. For Bitcoin, the script also
installs and configures the tor package of the distribution: `bitcoin.conf` connects through it
(`onion=127.0.0.1:9050`), and Bitcoin publishes its onion service with it. The seed node does not use it.

`/etc/default/bisq.env` holds the Bitcoin RPC password and the private key of the Burning Man oracle, and
`/bitcoin/bitcoin.conf` holds the RPC password. The script installs both with mode 600: `bisq.env` owned by root,
because systemd reads it as root, and `bitcoin.conf` owned by the user `bitcoin`, which runs bitcoind and
`bitcoin-cli`. The node does not get the two secrets on its command line, which every local user can read: at every
start, `bisq.service` writes `rpcPassword` and `bmOracleNodePrivKey` from `bisq.env` into `bisq.properties` in the data
directory of the node, for example `/bisq/bisq-seednode/bisq.properties`, with mode 600 for the user `bisq`. It
replaces these two lines there and keeps all other lines, so set the two values in `bisq.env` only. The script creates
`/bisq`, the home of the user `bisq` with the data directory of the node, with mode 700: the node writes its wallets
and logs readable by every user who can enter the directory. Only its key ring in `keys/` is owner-only by itself.

### Tor

Seed nodes run the Tor of the Bisq build in the sandbox, as every Bisq application does: the seed node starts that Tor
itself. The log names the level of the sandbox. If the system cannot enforce any level, the seed node stops with an
error which names the reason. Only then use one of the fallbacks. Without the sandbox, Tor runs with the rights of the
user `bisq` and can read the files of the seed node: to turn it off, add the line `useTorSandbox=false` to
`bisq.properties` in the data directory of the seed node, for example `/bisq/bisq-seednode/bisq.properties`. Or use a
running Tor which runs as another user, such as the tor service of the system.

To let the seed node use a running Tor instead of its own, for example the tor service of the system, where the system
cannot enforce the sandbox:

1. Give that Tor a control port on 127.0.0.1 with cookie authentication, and let the group of the cookie read the
   cookie and the data directory of that Tor, as `torrc` in this directory does (`ControlPort 9051`,
   `CookieAuthentication 1`, `CookieAuthFile /var/lib/tor/control_auth_cookie`, `CookieAuthFileGroupReadable 1` and
   `DataDirectoryGroupReadable 1`).
2. Add the user `bisq` to the group of the cookie, `debian-tor` on Debian and Ubuntu:
   ```bash
   sudo usermod -a -G debian-tor bisq
   ```
3. In `/etc/default/bisq.env`, set `BISQ_EXTERNAL_TOR_PORT` to the control port, for example `9051`, and
   `BISQ_EXTERNAL_TOR_CONTROL_COOKIE_FILE` to the cookie. At every start, `bisq.service` writes them to
   `external_tor.config` in the Tor directory of the seed node; with `BISQ_EXTERNAL_TOR_PORT=-1` it deletes that file.
4. Start the seed node after that Tor and restart it with it. Run `sudo systemctl edit bisq` and add:
   ```
   [Unit]
   After=tor@default.service
   Requires=tor@default.service
   ```
   The seed node authenticates with SAFECOOKIE, so a process which holds the control port instead of Tor gets no
   secret, and the start fails. The order keeps that from happening at boot.
5. Restart the seed node with `sudo systemctl restart bisq`.

With a running Tor, the seed node never starts its own Tor: if it cannot use the running Tor, it stops, and systemd
starts it again. The onion address does not change when you switch between the two, because the seed node keeps the
key of its onion service in `tor/hiddenservice` of its data directory and gives it to the Tor which it uses.

### Firewall

Next, configure your OS firewall to only allow SSH and Bitcoin P2P
```bash
ufw allow 22/tcp
ufw allow 8333/tcp
ufw enable
```

### Syncing

After installation, watch the Bitcoin blockchain sync progress
```bash
sudo tail -f /bitcoin/debug.log
```

After Bitcoin is fully synced, start the bisq service
```bash
sudo systemctl start bisq
sudo journalctl --unit bisq --follow
```

After Bisq is fully synced, check your Bitcoin and Bisq onion hostnames:
```bash
sudo -H -u bitcoin bitcoin-cli getnetworkinfo|grep address
sudo cat /bisq/bisq-seednode/btc_mainnet/tor/hiddenservice/hostname
```

### Testing

After your Bisq seednode is ready, test it by connecting to your new btcnode and bisq!

macOS:
```bash
/Applications/Bisq.app/Contents/MacOS/Bisq --seedNodes=foo.onion:8000 --btcNodes=foo.onion:8333
```

### Monitoring

If you run a main seednode, you also are obliged to activate the monitoring feed by running

```bash
bash <(curl -s https://raw.githubusercontent.com/bisq-network/bisq-monitor/refs/heads/main/scripts/install_collectd_debian.sh)
```
Follow the instruction given by the script and report your public key to the [Bisq monitoring maintainer](https://github.com/bisq-network/roles/issues/10)!

The script adds the option `--seedNodeReportingServerUrl` to `bisq.service` with a patch which fits only the unit of
Bisq 1.10.10 and earlier. On the current unit the patch fails without an error, and the node sends no reports. Set the
option in `bisq.properties` in the data directory of the node instead, which `bisq.service` keeps, and restart the
node:
```bash
echo "seedNodeReportingServerUrl=http://localhost:13003" | sudo -u bisq tee -a /bisq/bisq-seednode/bisq.properties
sudo systemctl restart bisq
```
Take the URL of `BISQ_REPORTINGSERVERURL` in `/etc/default/bisq.env` if it differs. The node then sends a report every
minute, and its log `bisq.log` in the data directory shows "Going to send report to monitor server".

### Upgrading

To upgrade your seednode to a new tag, for example v1.2.5
```bash
sudo -u bisq -s
cd bisq
git fetch origin
git checkout v1.2.5 # new tag
./gradlew clean build -x test
exit
sudo service bisq restart
sudo journalctl --unit bisq --follow
```

#### Files with secrets

Earlier versions of `install_seednode_debian.sh` installed `/etc/default/bisq.env` and `/bitcoin/bitcoin.conf` with
mode 644, so every local user can read the RPC password and the private key of the Burning Man oracle. Make both files
readable by their owner only; the owners stay as they are:
```bash
sudo chmod 600 /etc/default/bisq.env /bitcoin/bitcoin.conf
```
If the script did not install Bitcoin, protect the configuration of your Bitcoin node in the same way. The unit of
earlier versions also passed both secrets on the command line of the node, where every local user can read them while
the node runs; the new unit of "Upgrading to the Tor library" passes them through `bisq.properties` instead.

Earlier versions of the script also created `/bisq` with mode 755, so every local user can read the wallets and logs
of the node, and, until its first start with Bisq 1.10.11, its key ring in `keys/` of its data directory and the
backups of the keys. Make the directory private:
```bash
sudo chmod 700 /bisq
```
If a program of another user reads the JSON files which the node writes for a BSQ explorer (`BISQ_DUMP_BLOCKCHAIN`) or
for the markets (`BISQ_DUMP_STATISTICS`), give that user access to these files only, for example with ACLs, instead of
opening the whole directory.

#### Upgrading to the Tor library

Since Bisq uses the Tor library of the `tor/` submodule instead of netlayer, the options `--torControlPort`,
`--torControlCookieFile` and `--torControlUseSafeCookieAuth` stop the seed node at the start. The new `bisq.service`
no longer passes them. Before you restart the upgraded seed node, install the new unit and reload systemd:
```bash
sudo install -c -m 644 /bisq/bisq/seednode/bisq.service /etc/systemd/system/bisq.service
sudo sed -i -e "s/__BISQ_REPO_NAME__/bisq/" -e "s!__BISQ_HOME__!/bisq!" /etc/systemd/system/bisq.service
sudo systemctl daemon-reload
```
Adapt the paths if your installation differs, and apply the changes which `install_seednode_debian.sh` makes for the
`bitcoin.service` dependency again. The new unit drops `--seedNodeReportingServerUrl`, which the monitoring script
added to the old unit: on a monitored node, set the option in `bisq.properties` as "Monitoring" describes.

Switch the seed node to the Tor of the Bisq build in the sandbox at this upgrade (see "Tor" above); new installations
run it already. An installed `/etc/default/bisq.env` still sets `BISQ_EXTERNAL_TOR_PORT=9051`, with which the new unit
would keep the tor service of the system. Set `BISQ_EXTERNAL_TOR_PORT=-1` there, and remove the group `debian-tor`
from the user `bisq`, which no longer needs it: `sudo gpasswd -d bisq debian-tor`. Bitcoin keeps using the tor service.
After the restart, check that the log names a level of the sandbox. The onion address stays the same. Keep the tor
service for the seed node only if the system cannot enforce the sandbox; then add the drop-in of step 4 under "Tor",
because the new unit no longer starts after the tor service.

`BISQ_EXTERNAL_TOR_CONTROL_USE_COOKIE` in `/etc/default/bisq.env` is no longer used: the Tor library always
authenticates with the SAFECOOKIE method, in which tor proves that it knows the cookie before the seed node sends
anything. A seed node which used `--torControlPassword` sets `ControlPassword` in `external_tor.config` instead.

The Docker deployment in `deployment_v2/docker` is not supported with the Tor library. It configures its Tor container
with `--torControlHost` and `--torControlPassword`, which stop the seed node at the start, and that Tor runs in another
container, which cannot reach the onion service of the seed node: Tor connects to the target of `ADD_ONION` at
`127.0.0.1` of its own container.

### Uninstall

If you need to start over, you can run the uninstall script in this repo
```bash
sudo ./uninstall_seednode_debian.sh
```
WARNING: this script will delete all data!

