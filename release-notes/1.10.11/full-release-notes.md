# Bisq 1.10.11 Release Notes

> **Draft. The maintainer completes these points before the release and then removes this block:**
>
> - Decide whether the release contains more than `release-candidate-v1.10.11`. These notes cover that branch, which
>   holds `v1.10.10` and the pull requests #8168 to #8189. The local ref `upstream/master` (`18a1bdb973` of
>   2026-10-22) has 170 commits which are not on the branch. If the release takes some of them, add them to the range,
>   the counts and the commit summary.
> - Confirm the version number `1.10.11` (set by `546c81b1e6`), and set the release date and the "Generated on" date.
> - Link the commits to `https://github.com/bisq-network/bisq/commit/<hash>`. The hashes are those of
>   `release-candidate-v1.10.11`; they change if the branch is rebased.
> - The `tor` gitlink names `6edc00e7`, a commit of the library `main`.
> - Development builds with `2dd4d2ff87` but without `493f2e50c9` depended on the `tor` package (on
>   `master_henrik_private` from `223559db3e` up to the branch `linux-bundled-tor-sandbox-defaults`). Tell their
>   testers that the package stays installed and that its `tor@default` service keeps running on Debian and Ubuntu
>   until they remove the package (`docs/known-issues-with-installation.md`). No release depended on it.
> - Runtime checks which no note records yet: a sandboxed start of Bisq 1 (`sandbox-bisq1-start-unverified`), the
>   upgrade of a real netlayer data directory of v1.10.x and of one that started on v1.9.x
>   (`migration-netlayer-dir-upgrade-unverified`), and runs on macOS and Windows. On macOS, check whether webtunnel
>   bootstraps in the sandbox, which now lets it reach the certificate verifier of the system, and update the known
>   issue.
> - Open decisions which change what these notes say: `tor-dir-netlayer-leftovers-kept`,
>   `http-isolation-default-policy`, `backup-link-handling`, `torrc-file-inline-comment-refused` and
>   `tails-support-unassessed`. `seednode-docker-config-rejected-options` is resolved: `df4cd7481b` and `0bb1e71510`
>   update the Docker deployment.

These release notes cover the changes after `v1.10.10` (`fb552d3dc6`) through `e6925cd038` of
`release-candidate-v1.10.11`. The `tor` submodule is pinned at `6edc00e7` of the Tor library.

- Total commits: 116
- Non-merge commits: 96
- Merge commits: 20
- Files changed: 205
- Generated on: TODO (draft of 2026-10-27, updated on 2026-11-04)

## Compatibility and Operator Notes

- The application and packaging version is `1.10.11`.
- P2P, local database, and trade protocol version numbers remain unchanged. No protobuf definition changed.
- netlayer is replaced by the Tor library of the `tor` submodule. The key of the onion service stays in
  `tor/hiddenservice` in netlayer's format, so a node keeps its onion address.
- The first start moves the state of Tor into `tor/data/`. `tor/hiddenservice/` and `tor/external_tor.config` stay
  where they are.
- Bisq brings its own Tor on every operating system, as 1.10.10 did on Linux x86_64, macOS and Windows, and does not
  use the `tor` package of the distribution; the deb and rpm packages do not depend on it. Every platform, Linux ARM64
  included, gets tor 0.4.9.15 of Tor Browser 16.0. On Linux, Tor needs glibc 2.17 (2.27 on ARM64) and `libz.so.1` of
  the system.
- The programs of the pluggable transports come with the Tor bundle on every operating system, also on Linux ARM64.
- Versions before the Tor library read only the bridge lines which the Tor network settings store. For provided
  bridges, the settings store the built-in lines of the Tor bundle which the node installed in its Tor directory, when
  the window opens or the choice changes. Without an installed bundle, for example with a running Tor, which needs
  none, or before the first start of the node's own Tor, the stored lines are empty, and an earlier version starts
  Tor without bridges. On mainnet, the desktop application and the daemon refuse a downgrade; after a downgrade on
  another network, check the bridges in the Tor network settings.
- The options `--torControlHost`, `--torControlPort`, `--torControlPassword`, `--torControlCookieFile` and
  `--torControlUseSafeCookieAuth` stop the start of every Bisq application, also in `bisq.properties`, except on
  Whonix. A running Tor is configured in `external_tor.config` in the Tor directory instead; `ControlPassword` replaces
  `--torControlPassword`.
- Bisq does not start on a Whonix-Gateway. It runs on a Whonix-Workstation, which uses the Tor of the Gateway.
- A running Tor whose SOCKS port turns off `IsolateSOCKSAuth` with `NoIsolateSOCKSAuth` is refused; remove the flag from
  that `SocksPort`. A running Tor on another host which listens for SOCKS connections only on its loopback address is
  refused too; add a `SocksPort` on an address which the computer of Bisq can reach.
- Every Bisq application runs the Tor which it starts in the sandbox by default, also the seed node, the BTC node
  monitor and the inventory monitor. `--useTorSandbox=false` turns it off; the inventory monitor takes `0` as its
  seventh argument instead.
- `ClientTransportPlugin`, `ServerTransportPlugin`, `AccelName` and `AccelDir` entries of `--torrcOptions` or
  `--torrcFile` in `bisq.properties` stop the start of the Tor which Bisq starts; they are accepted on the command line
  only. With bridges, Bisq ignores `ClientTransportPlugin` entries, so they do not stop the start.
- Seed node operators must install the new `seednode/bisq.service` before they restart the upgraded node. The unit of
  earlier versions passes `--torControlPort`, `--torControlUseSafeCookieAuth` and `--torControlCookieFile`, also with
  `BISQ_EXTERNAL_TOR_PORT=-1`, so the upgraded node exits at once at every start, and systemd keeps restarting it or
  marks the unit as failed. Operators of other nodes whose start command passes one of the `--torControl*` options,
  for example a bridge node, remove the options and write `external_tor.config` instead.
- Seed nodes run the Tor of the Bisq build in the sandbox instead of the tor service of the system; new installations
  do so already. An installed `/etc/default/bisq.env` keeps `BISQ_EXTERNAL_TOR_PORT=9051`, so operators switch an
  upgraded seed node: set `BISQ_EXTERNAL_TOR_PORT=-1` there, remove `bisq` from the group `debian-tor`, and check after
  the restart that the log names a level of the sandbox. The onion address stays the same. Only where the system
  cannot enforce the sandbox does a seed node keep the tor service, with the drop-in which `seednode/README.md`
  describes, because the new unit no longer starts after `tor@default`.
- Seed, statistics, bridge and REST nodes and the inventory monitor exit with status 1 when Tor or the onion service
  cannot start, so that systemd or the start script restarts them, and so does the BTC node monitor when its Tor
  cannot start.
- Seed node operators run `sudo chmod 600 /etc/default/bisq.env /bitcoin/bitcoin.conf`. Earlier versions of
  `install_seednode_debian.sh` installed both files with mode 644, so every local user can read the Bitcoin RPC
  password and the private key of the Burning Man oracle. New installations get mode 600. The new unit no longer
  passes these two secrets on the command line of the node, which every local user can read; it writes them into
  `bisq.properties` in the data directory of the node with mode 600.
- Seed node operators also run `sudo chmod 700 /bisq`. Earlier versions of the install script created the home of the
  user `bisq` with the data directory of the node with mode 755, and the node writes its wallets and logs readable by
  every user who can enter the directory. New installations get mode 700.
- Monitored seed nodes set `seedNodeReportingServerUrl=http://localhost:13003` in `bisq.properties` of the data
  directory. The monitoring script of bisq-monitor adds the option to `bisq.service` with a patch which does not apply
  to the new unit, so without this line the node sends no reports.
- The Docker deployment of the seed node in `seednode/deployment_v2/docker` runs Tor in the container of the seed
  node instead of the service `bisq-tor`. Operators remove the stack and deploy it again, as the README of the seed
  node describes.
- Release builds use Azul Zulu 21.0.13 and JavaFX 21.0.13. The release build check compares the Java update by its
  version numbers, so another Azul build of 21.0.13 can become the release build by changing only the pinned runtime
  build.
- Malformed signed witnesses, dispute agent registrations and private notifications no longer stop the start of a
  node or the handling of other data. A signed witness which was stored before the new checks is removed when the
  store is read.

## Notable Changes

### Tor Library Instead of netlayer

P2P connections, the onion service and the SOCKS proxy for Bitcoin and HTTP requests now go through `TorService` of
the Tor library, which is a Git submodule of the repository and which Bisq 2 also uses. netlayer, tor-binary, the
JitPack builds of jsocks and jtorctl and kotlin-logging-jvm are removed, with their verification metadata.

The key of the onion service stays in `tor/hiddenservice/private_key` in the format which netlayer wrote. Bisq derives
the onion address from the key, as Tor does, and rewrites a missing or wrong `hostname`. A generated key is written in
netlayer's format, so that an earlier version can read it.

On every operating system, Bisq runs the Tor of the Tor expert bundle of Tor Browser 16.0 (tor 0.4.9.15), whose PGP
signature, SHA-256 hash and tor version the build verifies. netlayer brought tor 0.4.9.14 in 1.10.10. Linux ARM64, for
which netlayer had no Tor, gets the bundle of the same stable release. The bundles bring the pluggable transports
`lyrebird` and `conjure-client`. Bisq uses neither a `tor` nor transports on the `PATH`, and the Linux packages do not
depend on the `tor` package. On Linux, Tor needs glibc 2.17 (2.27 on ARM64) and `libz.so.1` of the system. The bundled
Tor gets its GeoIP files and an empty OpenSSL configuration in the Tor directory instead of the paths below
`/var/tmp/dist` which its binary has compiled in.

The Tor directory gets the parts `bin/` (the bundle), `data/` (the data directory of Tor) and `sandy/` (the sandbox
program). If Tor does not bootstrap within 4 minutes, Bisq deletes the Tor files except `hiddenservice/` and
`external_tor.config` and starts Tor again, up to 5 starts, as netlayer did. This deletion, and "Delete outdated Tor
files and shut down" in the Tor network settings, no longer follow symbolic links or junctions.

At the shutdown, the node waits up to 8 seconds for the close of its connections and the stop of its Tor. The Tor
library also stops and waits for the processes below Tor, such as the programs of pluggable transports. Processes
which it could not stop stay tracked: the next start stops them first, and a later shutdown reports them. If the
cleanup after a failed start fails as well, the error still names the cause of the failed start. The BTC node monitor
also waits up to 8 seconds for the stop of its Tor; before, it waited 2 seconds and could exit while Tor still ran.
On Linux, Tor runs in a session of its own, so a Ctrl+C in the terminal of the node reaches only the node, whose
shutdown still uses Tor and stops it at its end.

### Running Tor

A running Tor is configured in `external_tor.config` in the Tor directory: `UseExternalTor 1`, `ControlPort`, and
`CookieAuthentication 1` with `CookieAuthFile`, or `ControlPassword` for a Tor which uses `HashedControlPassword`.
`TOR_SKIP_LAUNCH` and Whonix also select a running Tor. With a cookie, Bisq authenticates with SAFECOOKIE, in which Tor
proves that it knows the cookie before Bisq sends anything. Bisq waits up to 4 minutes for the running Tor to bootstrap.
If it cannot use the running Tor, the start fails with an error which names the host, the port and the cause; Bisq
never starts its own Tor instead, which would connect without the settings of the running Tor.

Bisq connects to the SOCKS listener which the running Tor reports: at its address, or at the host of the control port
if the listener is on all addresses. netlayer connected to the reported port on the host of the control port. A Tor
on another host which listens for SOCKS connections only on its loopback address cannot be used, and the error says
to add a `SocksPort` on an address which this host can reach. Bisq refuses a running Tor whose SOCKS port does not
isolate streams by their SOCKS credentials (`NoIsolateSOCKSAuth`), because Bisq keeps its peer connections apart from
its other connections with them; the error names the `SocksPort` entry. On Whonix, onion-grater does not let Bisq read
this configuration, so Bisq logs that it could not check the port. Bisq connects to the control port again only while
the connection is refused, up to ten times: a host which does not answer fails the start after 10 seconds, and a host
which cannot be reached fails it at once.

The five `--torControl*` options, which configured a running Tor for netlayer, stop the start with an explanation
which names `external_tor.config` and the config file. The desktop application shows this explanation, and every
other invalid option at the start, in a dialog, because it is usually started without a terminal. On a
Whonix-Workstation, Bisq ignores the options with a warning, because it always uses the Tor of the Whonix-Gateway
there; it needs the Bisq profile of onion-grater on the Gateway. Bisq does not start on the Whonix-Gateway itself:
the error says to run Bisq on a Whonix-Workstation. The options are ignored there with a warning too, so that this
error is the one which the user sees.

### Bridges

The node reads the choice of the Tor network settings at every start: no bridges, the provided bridges of a type, or
custom bridge lines. The provided bridges are the built-in bridges of the Tor Browser version of the bundled Tor,
instead of the lists of about 2019 which the settings stored when the user chose them. The types are obfs4, snowflake
and meek; a stored obfs3 choice becomes snowflake, and meek-amazon and meek-azure become meek. If the chosen bridges
cannot be used, the start fails, and Bisq never connects without them. When Tor cannot connect, the desktop
application opens the Tor network settings again, as with netlayer. Bridge lines are no longer written to the log,
because a bridge which a user got privately is blocked once it is known.

### Tor Sandbox

Every Bisq application runs the Tor which it starts in the sandbox of sandy 1.0.0, the first public release of sandy,
also the seed node, the BTC node monitor and the inventory monitor. Tor may read its torrc and the bundle
and write only its data directory, has a network of its own, and cannot start other processes or signal processes
outside the sandbox. Where the system cannot enforce this, sandy uses a weaker level, which the log names with a
warning; the desktop application also shows a notice for a level with the network of the computer or without isolated
signals, which says what the level gives up and when Tor gets the stronger protection, until the user chooses "Don't
show again" for that combination of weaknesses. If no level can be enforced, the start fails with an error which
names the reason and `--useTorSandbox=false`; Bisq never starts Tor without the sandbox instead. The inventory monitor,
which takes positional arguments, turns the sandbox off with `0` as its seventh argument, and its error names that
argument. A running Tor runs without the sandbox of the node. The build takes the sandy archive of each platform from
the GitHub release of sandy. It checks the archive against its pinned SHA-256 hash and against the `SHA256SUMS` file of
the release, which must carry a signature by the pinned sandy release key.

Bridges with pluggable transports need `--allowPluggableTransportsInTorSandbox=true`. The switch "Allow pluggable
transports in the Tor sandbox" of the Tor network settings asks for a confirmation, writes the option into
`bisq.properties` and takes effect after a restart. It is disabled while the sandbox is off, on Windows, and while the
command line sets the option. When the transports of the chosen bridges are not allowed, the error names them and the
switch, and the Tor network settings open. On Windows, Tor cannot start pluggable transports in the sandbox, so
bridges which need them need `--useTorSandbox=false`. On macOS, the sandbox lets Tor and its transports reach the
certificate verifier of the system only for webtunnel, snowflake and conjure bridges, which verify their server with
it; obfs4 and meek bridges do not get it.

An error advises to turn the sandbox off only if sandy refused to run Tor, which Bisq reads from the status record
of sandy, a file which Tor can neither write nor remove. A sandboxed Tor which ends before it answers gets an error
without that advice, so that a compromised Tor cannot talk the user into turning the sandbox off. When sandy refuses
before Tor runs, for example because another process took one of the ports which Bisq chose for the sandbox, Bisq
starts Tor once more with new ports. The error of a failed sandboxed start quotes the standard error of sandy and Tor
last, labeled as not verified by Bisq, because a sandboxed Tor can write that file. The desktop application shows
such a refusal, and the refusals of a running Tor and of a Whonix-Gateway, without its generic advice to check the
internet connection or to restart, because the error says itself what helps.

When Tor runs without the sandbox after it ran in it, Bisq first clears the data directory of Tor, so that Tor with
all rights of the user does not read what a compromised sandboxed Tor may have written there. Tor then selects new
entry guards and downloads the directory again, so this start takes longer. The key of the onion service is not in
the data directory and stays.

### Torrc Options

`--torrcFile` and `--torrcOptions` still add entries to the torrc. Entries for `DataDirectory`, `ControlPort`,
`ControlPortWriteToFile`, `HashedControlPassword`, `SocksPort` and `DisableNetwork` are ignored with a warning, in any
case and as abbreviations, and so are entries which would give another program control over Tor: `ControlSocket`,
`ControlSocketsGroupWritable`, `CookieAuthentication`, `CookieAuthFile`, `CookieAuthFileGroupReadable`,
`__HashedControlSessionPassword` and `__OwningControllerFD`. An entry with a line break, or a value with `#`, stops
the start, so an inline comment after a value in the file of `--torrcFile`, which netlayer accepted, now stops the
start. `ClientTransportPlugin` and `ServerTransportPlugin` entries make Tor start a program, and `AccelName` and
`AccelDir` entries make it load a library; they are accepted only if the command line sets the option which brings
them, because whoever can change `bisq.properties` could otherwise run code at the next start. The Tor library refuses
them, and a running Tor, which gets no torrc entries from Bisq, is not affected. With bridges, Bisq sets the transports
of the bridges itself and ignores `ClientTransportPlugin` entries with a warning, so they do not stop the start.

### Stream Isolation of HTTP Requests

With `--torStreamIsolation`, each HTTP request through Bisq's Tor sends new SOCKS credentials, and Bisq performs the
SOCKS5 handshake itself, so that Tor puts the request on a circuit of its own. Before, the SOCKS client of the Java
runtime dropped the credentials, and all HTTP requests shared one isolation group, also with the option. Without the
option, the behaviour is unchanged.

### Logs and Backup

Tor writes its log to `tor/tor-stdout.log`, which moves to `tor-stdout.log.1` above 10 MiB, and its standard error to
`tor/tor-stderr.log`, where sandy also says why it did not start Tor. netlayer wrote Tor's output to the Bisq log.
"Zip Log files" adds these files, the earlier log `tor-stdout.log.1` included. The control traffic of Tor is logged at
debug level only, because it contains onion addresses.

The zip leaves out the relays and bridges which the logs name, because a privately obtained bridge is blocked once it
is known and users share the zip. From the logs of Tor it removes fingerprints, nicknames, ed25519 identities, IPv4
addresses and IPv6 addresses with or without brackets, also those which end with `::`, the arguments of bridge lines
such as `cert` and `url`, and the URLs and host names in the messages of the pluggable transports, where lyrebird names
the host of a webtunnel bridge, also host names under a Punycode top level domain.
The versions of Tor and loopback addresses stay. From the Bisq log it removes `HOSTID` and `HOSTADDR` of the bootstrap
events which the Tor library logs. The files on disk are unchanged.

"Backup now" does not copy the data directory of Tor, which a sandboxed Tor may write, nor `tor/data-mode`, the record
whether a sandboxed Tor wrote it, which would be wrong next to another data directory. It copies only regular files and
directories, does not follow symbolic links or junctions, and names the entries which it did not copy in a warning. On
file systems with POSIX permissions, the backup creates its directories with `0700` and its files with `0600`.

The private keys of the node, `keys/sig.key` and `keys/enc.key` in the data directory, were written with the default
mode, readable by every local user who could enter the data directory, and so were their rolling backups in
`keys/backup`. On file systems with POSIX permissions, the `keys` directory is now `0700` and the key files `0600`,
also those of earlier versions from the next start on, as for the key of the onion service.

### Seed Nodes and Other Headless Nodes

Seed, statistics, bridge and REST nodes and the inventory monitor shut down with exit status 1 when Tor or the onion
service cannot start, so that systemd or their start script restarts them. Before, they kept running without the P2P
network until their periodic restart. The BTC node monitor also exits with status 1 when its Tor cannot start, and
logs the reason, for example a refusal of the sandbox; before, it kept its HTTP server running without Tor and wrote
the reason only to the standard error.

The seed node starts its own Tor, the Tor of the Bisq build, and runs it in the sandbox, instead of using the tor
service of the system: `seednode/bisq.env` sets `BISQ_EXTERNAL_TOR_PORT=-1`, and `seednode/bisq.service` no longer
starts after `tor@default` or requires it. `install_seednode_debian.sh` installs the `tor` package only for Bitcoin,
which connects through it, and no longer adds the user `bisq` to the group `debian-tor`. A running Tor stays possible:
for a port other than `-1`, the unit writes the settings from `/etc/default/bisq.env` to `external_tor.config`, and
`seednode/README.md` describes what the operator sets up, including a drop-in which starts the node after
`tor@default`, because with SAFECOOKIE a process which holds the control port instead of Tor gets no secret and the
start fails. The unit no longer passes the `--torControl*` options. It sends SIGTERM only to the node
(`KillMode=mixed`), so that the node keeps its own Tor until the end of its shutdown; systemd kills what is left once
the node exited, or after 150 seconds. A stop no longer leaves the unit failed: after SIGTERM the JVM ends with the exit
status 143 once its graceful shutdown ran, and the unit takes 143 as a clean stop (`SuccessExitStatus=143`). The node
still exits with status 1 when it wants to be restarted.

The unit of earlier versions passes `--torControlPort`, `--torControlUseSafeCookieAuth` and `--torControlCookieFile`
to the node, which now stop its start, also when `BISQ_EXTERNAL_TOR_PORT` is `-1`. Nothing migrates an installed unit.
Before the first start of the upgraded seed node, install the new unit, replace its placeholders as
`install_seednode_debian.sh` does, and reload systemd, for example for an installation in `/bisq`:

```bash
sudo install -c -m 644 /bisq/bisq/seednode/bisq.service /etc/systemd/system/bisq.service
sudo sed -i -e "s/__BISQ_REPO_NAME__/bisq/" -e "s!__BISQ_HOME__!/bisq!" /etc/systemd/system/bisq.service
sudo systemctl daemon-reload
```

Apply the changes for the `bitcoin.service` dependency which `install_seednode_debian.sh` makes again, if the node
uses them. An installed `/etc/default/bisq.env` keeps `BISQ_EXTERNAL_TOR_PORT=9051`, so the upgraded node keeps using
the tor service of the system. To switch to its own Tor, set `BISQ_EXTERNAL_TOR_PORT=-1` there; the user `bisq` then
no longer needs the group `debian-tor`. To keep the tor service, add the drop-in of `seednode/README.md`. If the node
was already restarted with the old unit, `journalctl --unit bisq` shows "option 'torControlPort' is not supported
anymore", and `sudo systemctl reset-failed bisq` clears the failed state before the restart.
`BISQ_EXTERNAL_TOR_CONTROL_USE_COOKIE` in `/etc/default/bisq.env` is no longer used, because the node always
authenticates with SAFECOOKIE. A seed node which used `--torControlPassword` sets `ControlPassword` in
`external_tor.config` instead. `seednode/README.md` describes the same steps.

`install_seednode_debian.sh` installs `/etc/default/bisq.env` and `/bitcoin/bitcoin.conf` with mode 600: `bisq.env`
owned by root, because systemd reads it as root, and `bitcoin.conf` owned by the user `bitcoin`, which runs bitcoind.
Earlier versions installed them with mode 644, so every local user can read the Bitcoin RPC password of both files
and the private key of the Burning Man oracle in `bisq.env`. On existing installations, run
`sudo chmod 600 /etc/default/bisq.env /bitcoin/bitcoin.conf`, or protect the configuration of your own Bitcoin node in
the same way. The unit of earlier versions also passed both secrets on the command line of the node, which every
local user can read in `/proc` while the node runs. At every start, the new `bisq.service` writes `rpcPassword` and
`bmOracleNodePrivKey` from `bisq.env` into `bisq.properties` in the data directory of the node, the config file which
the node reads, with mode 600 for the user `bisq`; it replaces these two lines there and keeps the other lines.

The script creates `/bisq`, the home of the user `bisq` with the data directory of the node, with mode 700 instead of
755. The node writes its wallets and its logs with the default umask, readable by every user who can enter the
directory; earlier versions also wrote the key ring in `keys/` that way. On existing installations, run
`sudo chmod 700 /bisq`. A program of another user which
reads the JSON files of a BSQ explorer or of the markets gets access to these files only, for example with ACLs.

The install script of bisq-monitor adds `--seedNodeReportingServerUrl=${BISQ_REPORTINGSERVERURL}` to `bisq.service`
with a patch whose context contains `--torControlPort`, so it applies only to the unit of 1.10.10 and earlier, fails
on the new unit without an error, and the node sends no reports to the monitor. Installing the new unit over a patched
one drops the option as well. Monitored seed nodes set it in `bisq.properties` of the data directory instead, which
the unit keeps, for example
`echo "seedNodeReportingServerUrl=http://localhost:13003" | sudo -u bisq tee -a /bisq/bisq-seednode/bisq.properties`,
and restart the node. `seednode/README.md` describes it under "Monitoring".

The Docker deployment in `seednode/deployment_v2/docker` no longer has the service `bisq-tor` and no longer passes
`--torControlHost` and `--torControlPassword`. The seed node starts the bundled Tor in its own container, in the
sandbox by default. Tor in another container could not reach the onion service of the node, because Tor forwards the
connections of the onion service to its own loopback address; only outbound connections worked. The onion key stays
in the data volume of the node, so the onion address does not change. If the container cannot enforce the sandbox,
`useTorSandbox=false` in `seednode_config` turns it off. Operators remove the stack and deploy it again, because
`docker stack deploy` without `--prune` keeps the old `bisq-tor` service. The upgrade instructions of the seed node
also initialize the `tor` submodule.

On a Turkish default locale, enum options such as `--baseCurrencyNetwork=btc_mainnet` of the shipped
`seednode/bisq.env`, values of the daemon API and the CLI, and the network files of the inventory monitor and the BTC
node monitor were not found, because their case was converted with the default locale. Bisq now converts the case of
these identifiers with `Locale.ROOT`. `bisq.service` derives the name of the network directory as the node does, so
a `BISQ_BASE_CURRENCY` in upper case no longer puts `external_tor.config` and the DAO JSON files into the wrong
directory.

### Validation of Network Data

Several types of network data which anyone can publish made a signature check throw an exception instead of failing.

- Signed witnesses: a malformed signer key or signature now gives an invalid signature. Before, the exception broke
  the sign state and the trade limit checks of the account. A signed witness with an empty account hash or key, or
  with an unknown verification method, is no longer stored. Before, indexing it stopped the start of every node which
  received it, also of seed nodes, which served it to every new node. Such a witness which was stored earlier is
  removed when the store is read.
- Copies of a signed witness with another owner: the signature and the signer key verify in several encodings, and
  each encoding gave the witness another hash, so a copy was stored next to the original. A copy of an arbitrator's
  witness made its key a signer at once. Only the encodings which bitcoinj creates for an arbitrator's witness and the
  JDK creates for a trade witness count now. The mainnet arbitrator keys signed their last witness in January 2021,
  so a witness of one of these keys dated 2021-02-01 or later is invalid. A test checks that every witness of the
  bundled mainnet store still passes the rules.
- Dispute agents: a malformed registration key or signature of a mediator or refund agent stopped the start of the
  node and emptied the registry of a running node. It is now rejected, and the registry is replaced only after all
  agents were checked. An agent which anyone published with the address of a registered agent could remove that agent
  from the registry; all agents are now checked first, and of several registered agents with the same address the
  latest registration counts.
- Private notifications: a malformed signature skipped the rest of the mailbox messages at every start until the
  notification expired. It now counts as invalid. Only a verified notification is kept, so an invalid notification no
  longer makes the shown one return at the next start.

Bisq opens only `http` and `https` links. Before, a link of a popup text or of DAO data, which anyone can publish, was
passed to the opener of the system also as a `file:` or `smb:` link.

### Build Tooling

Release builds use Azul Zulu 21.0.13, and JavaFX is updated to 21.0.13. Linux ARM builds use the linux-aarch64 JavaFX
artifacts, which JavaFX publishes since 21.0.11. The dependency verification instructions follow those of Bisq 2, and
the Tor upgrade instructions describe the upgrade through the `tor` submodule.

`verifyReleaseEnvironment` compares the Java update by its version numbers: the numbers of the running JDK must start
with those of `releaseBuild.javaVersion`. Azul builds one update more than once, and a second build of 21.0.13 reports
`21.0.13.0.3`. The runtime build, the Zulu build, the vendor and the Gradle version are still compared exactly,
because the installers bundle a runtime made from the JDK of the build. Bisq 1 has no build environment check in
`check`, so development builds are unchanged.

The dependency audit and `dependencySignatureReport` cover the included `tor` build and its build-logic. The audit
resolves the configurations of each project in that project, as Gradle requires. The sandy 1.0.0 archives are
reviewed checksum-only entries of the verification metadata, because they have no detached signature.

## Known Issues

- On Windows, bridges with pluggable transports need the sandbox off. On macOS, webtunnel fails in the sandbox.
- At a sandbox level with the network of the system, and with pluggable transports other than obfs4 on Linux, the
  sandbox does not keep Tor from connecting to local services, such as a local Bitcoin node or the API of the daemon.
- The Bitcoin connections drop the SOCKS credentials of `--torStreamIsolation`, so they share one isolation group.
- On macOS, a Ctrl+C in the terminal of the node ends Tor at once, because macOS has no `setsid`.
- Upgraded nodes keep files of netlayer's Tor in the Tor directory that nothing uses, for example netlayer's Tor and
  its libraries on Linux. "Delete outdated Tor files and shut down" in the Tor network settings removes them.
- The daemon logs a failed start of Tor and keeps running without the P2P network.

## Tests and Documentation

New specifications describe the Tor startup and configuration (`docs/specifications/network/tor-startup.md`), the
identity of the onion service (`onion-service-identity.md`), the stream isolation (`tor-stream-isolation.md`) and the
backup of the data directory (`docs/specifications/application/data-directory-backup.md`). The specifications of the
signed witness describe the new rules for malformed and copied witnesses. `docs/tor-upgrade.md`
describes the upgrade of Tor through the `tor` submodule and the pins of the bundles, `docs/build.md` and the known
installation issues say that Bisq brings its own Tor on Linux and what it needs from the system, and
`seednode/README.md` describes the seed node with its own Tor in the sandbox and with a running Tor. Unit tests cover
the key formats, the bridge settings, the start failures, the torrc rules, the HTTP isolation, the backup and the
shutdown. This release note summarizes source changes; it does not claim that release builds or tests were run for
this note.

## Commit Summary

The commits of `release-candidate-v1.10.11` after `v1.10.10`, without merge commits, in the order of their pull
requests.

| Pull request | Commit | Summary |
| --- | --- | --- |
| #8168 | `07af1b95e0` | build: remove the unused Tor download plugins of build-logic |
| #8168 | `a4c13d84d3` | doc: adopt the dependency verification instructions of Bisq 2 |
| #8168 | `546c81b1e6` | build: set version 1.10.11 |
| #8168 | `ace34126c9` | build: audit build-logic and turn the verification key servers off |
| #8168 | `2e6f4e01bd` | doc: name the seed node uninstall script as it is called |
| #8169 | `15224159c5` | build: update the pinned Java version to Azul Zulu 21.0.13 |
| #8169 | `043aa0164f` | build: update JavaFX to 21.0.13 and use its linux-aarch64 jars |
| #8170 | `25075eeea2` | refactor: parse the torrc options in Bisq instead of netlayer |
| #8170 | `44d141b7f8` | refactor: keep external_tor.config when the Tor files get deleted |
| #8170 | `6d09422416` | build: add the Tor library as the Git submodule tor |
| #8170 | `ecb30afaef` | refactor: use the Tor library instead of netlayer |
| #8170 | `1b73ac43f1` | refactor: stop Tor and close the server socket without the Tor worker |
| #8170 | `2dd4d2ff87` | build: depend on the tor package on Linux |
| #8170 | `cb7457788a` | build: remove netlayer |
| #8170 | `d08e48470f` | doc: specify the onion service identity and the Tor startup |
| #8170 | `99aa8c448a` | build: audit and report the dependencies of the included builds |
| #8170 | `8b852db86f` | fix: pin exact Zulu build in CI |
| #8170 | `b373b1e3d5` | fix: verify exact Zulu runtime build |
| #8171 | `bea4f7fee8` | fix: update the Tor library to its bug fixes and adapt to them |
| #8171 | `eb3ab4b48c` | fix: join a second shutdown of the Tor node and wait for its Tor |
| #8171 | `5a8cc18ae3` | test: reset the UserThread statics of the Tor node tests in one place |
| #8171 | `74e439ccb2` | fix: exit with the failure status when a headless node cannot start Tor |
| #8171 | `35e470260b` | fix: take the exit status 143 of a stopped seed node as success |
| #8171 | `463fd31b77` | fix: convert the case of options, API and CLI values with Locale.ROOT |
| #8172 | `6d6b90fe0d` | hardening: update the Tor library to its hardening |
| #8172 | `ded4678005` | hardening: delete the Tor files without following links or junctions |
| #8172 | `2de5f5d393` | hardening: back up the data directory owner-only and without links |
| #8172 | `cbbfffa320` | fix: let only the seed node stop its own Tor at a systemd stop |
| #8172 | `3a21bec4ee` | hardening: send the stream isolation credentials with HTTP requests |
| #8172 | `94fb6c7ec8` | hardening: keep the private keys of the key ring owner-only |
| #8173 | `493f2e50c9` | feature: update the Tor library, use SAFECOOKIE and bundle Tor on Linux |
| #8173 | `01bac25c96` | feature: always report why a running Tor cannot be used |
| #8173 | `dfbcc99cfb` | feature: use bridges again and ask for them when Tor cannot connect |
| #8173 | `6409e87026` | fix: fail at once when a running Tor does not bootstrap in time |
| #8173 | `2723964e89` | doc: say that Bisq brings its own Tor on Linux too |
| #8173 | `ea1bdabf23` | feature: ignore Tor options on Whonix and show invalid ones in a dialog |
| #8173 | `0a9ec64721` | test: cover an interrupted Tor start and the seed node unit |
| #8174 | `b786a8c197` | sandy: update the Tor library to its sandbox integration |
| #8174 | `f3cf0f2977` | sandy: run the Tor of the node in the sandbox by default |
| #8174 | `0685dfd0ea` | sandy: let the user allow pluggable transports in the Tor sandbox |
| #8174 | `6e39623c57` | hardening: refuse torrc entries which start programs from config files |
| #8174 | `74c3de393b` | sandy: explain the refusal of bridge transports in Bisq 1 terms |
| #8174 | `cf0706ea7e` | sandy: show why Tor failed in the Tor network settings |
| #8174 | `6cedf0b411` | sandy: run the Tor of every application in the sandbox by default |
| #8174 | `ed37679193` | sandy: show a notice when Tor runs in a weak level of its sandbox |
| #8174 | `d07e7da848` | hardening: skip the Tor data directory in the backup |
| #8174 | `153eb61a36` | fix: add the logs of Tor to the zip of the log files |
| #8174 | `8b694e5e59` | fix: leave the data mode record of Tor out of the backup |
| #8174 | `1004f24788` | doc: state the rules of the Tor library in the Tor specifications |
| #8174 | `250efb31b0` | fix: show a start failure which says what helps without generic advice |
| #8174 | `2517a40332` | fix: offer the fixes when the Tor sandbox refuses the bridges |
| #8174 | `39c94e0e67` | fix: derive the network directory in bisq.service as the node does |
| #8174 | `21dfda8edd` | hardening: keep seed node secrets and data away from other local users |
| #8174 | `8498d3bb35` | doc: tell monitored nodes to set the reporting URL in bisq.properties |
| #8174 | `125a6746c7` | fix: shut the monitors down with the failure status if Tor cannot start |
| #8174 | `bb230593b4` | fix: find the files of the network in both monitors in every locale |
| #8174 | `7d291aabc8` | test: run the Tor network node test on a private Tor network |
| #8174 | `438961afc7` | doc: add the draft release notes of 1.10.11 |
| #8174 | `15d11f86b5` | build: pin the published Tor main commit |
| #8175 | `692f5f6980` | Update tor library |
| #8176 | `893ae54274` | Initialize submodules when upgrading seed nodes |
| #8176 | `28790c0e68` | Export keys when writing verification metadata |
| #8176 | `a3891a3448` | Document configuration-time metadata bootstrap |
| #8176 | `865017dac3` | Enable key servers in refresh instructions |
| #8176 | `e089c49b36` | Allow Tor shutdown to finish in the node monitor |
| #8176 | `697240b24a` | Match trade categories consistently after parsing |
| #8176 | `df4cd7481b` | Migrate Docker seed-node external Tor settings |
| #8176 | `f3874247d4` | Resolve audit dependencies in owning projects |
| #8176 | `0bb1e71510` | fix: run the Tor of the Docker seed node in its own container |
| #8177 | `144622ace3` | build: compare the CI Java build with the pinned update by version numbers |
| #8177 | `376ed4a0ff` | build: compare the release JDK with the pinned Java update by version numbers |
| #8178 | `01092d0731` | Update tor lib 8234d49c |
| #8181 | `f54a511c2e` | test(p2p): run disconnect network test on its own timer |
| #8180 | `65fb5a794b` | docs: describe the Tor of the Docker seed node in its own container |
| #8182 | `c10b178876` | Update the tor library to 8234d49 |
| #8183 | `7ebf727ff8` | Update to tor lib ff6cb9a0f45ed3d23ef74f005586b139a458fa7a |
| #8183 | `dff18fbe2f` | hardening: let the Tor library refuse code-running torrc entries of config files |
| #8183 | `5bdcc40906` | docs(release): name AccelName and AccelDir among the refused torrc entries |
| #8183 | `2f6380449f` | test(p2p): cover transport plugin refusals through the Tor library |
| #8183 | `a31479db1d` | docs: name the bridge exception of the refused transport plugin entries |
| #8186 | `cce4f07996` | Reject malformed dispute agent registrations |
| #8186 | `b245ddbb70` | Verify dispute agents before reducing by address |
| #8188 | `690559297d` | Ignore a malformed private notification signature |
| #8188 | `d0fb0ce052` | Remove only the shown private notification |
| #8188 | `5f2c7a38f4` | Open only http and https links |
| #8187 | `44526cc255` | fix(core): treat a malformed signed witness key as invalid |
| #8187 | `ce1707da39` | fix(core): keep malformed signed witnesses out of the store |
| #8187 | `b58c82efcb` | fix(core): reject copies of a signed witness with another owner |
| #8187 | `0380d51d2c` | test(core): check the bundled witnesses against the rules |
| #8187 | `43c21132a4` | docs: update the signed witness specifications |
| #8185 | `9163bf852e` | feat(core): let the log zipper redact the text of a file |
| #8185 | `8b8aaa5f38` | fix: zip the logs without the relays and bridges |
| #8185 | `0368f3672a` | docs: describe what the log zip leaves out |
| #8185 | `4334c3eea9` | fix(p2p): redact IPv6 addresses ending in :: and Punycode host names |
| #8189 | `b6095e7b53` | Update to tor lib 6edc00e7413a1a95767586abc4899a8f0202ac85 |
| #8189 | `fd7df3cd3c` | build: allow the checksum-only sandy 1.0.0 release archives |
