# Bisq 1.10.11 Release Notes

> **Draft. The maintainer completes these points before the release and then removes this block:**
>
> - Add the other changes of the release. These notes cover only the switch from netlayer to the Tor library and the
>   changes that came with it. The local ref `upstream/master` (`18a1bdb973` of 2026-10-22) has 170 commits which are
>   not in the range below. Update the range, the counts and the commit summary to the commits of the release.
> - Confirm the version number `1.10.11` (set by `2303377050`), and set the release date and the "Generated on" date.
> - Link the commits to `https://github.com/bisq-network/bisq/commit/<hash>` once they are there. Their hashes change
>   if the branches are rebased.
> - Check that the `tor` gitlink names a commit of the library `main` which contains the library changes that these
>   notes describe. The branch `WT-fix-open-items-after-bundled-tor` moved it to a library branch of the same name
>   before that branch was merged.
> - Development builds from `223559db3e` up to the branch `linux-bundled-tor-sandbox-defaults` depended on the `tor`
>   package. Tell their testers that the package stays installed and that its `tor@default` service keeps running on
>   Debian and Ubuntu until they remove the package (`docs/known-issues-with-installation.md`). No release depended on
>   it.
> - Runtime checks which no note records yet: a sandboxed start of Bisq 1 (`sandbox-bisq1-start-unverified`), the
>   upgrade of a real netlayer data directory of v1.10.x and of one that started on v1.9.x
>   (`migration-netlayer-dir-upgrade-unverified`), and runs on macOS and Windows. On macOS, check whether webtunnel
>   bootstraps in the sandbox, which now lets it reach the certificate verifier of the system, and update the known
>   issue.
> - Open decisions which change what these notes say: `tor-dir-netlayer-leftovers-kept`,
>   `http-isolation-default-policy`, `backup-link-handling`, `torrc-file-inline-comment-refused`,
>   `tor-log-leaks-private-bridges-in-support-zip`, `tails-support-unassessed` and
>   `seednode-docker-config-rejected-options`.

These release notes cover the changes after `v1.10.10` (`fb552d3dc6`) through `8072e5ef3c` of `master_henrik_private`,
and the commits of the branches `fix-open-items-after-crosscheck` and `linux-bundled-tor-sandbox-defaults` which follow
`8072e5ef3c`.

- Total commits: 71 through `8072e5ef3c`, and 4 commits of `fix-open-items-after-crosscheck` before these notes
- Non-merge commits: 55 through `8072e5ef3c`, and 4
- Merge commits: 16
- Files changed: 126 through `8072e5ef3c`
- Generated on: TODO (draft of 2026-10-27)

## Compatibility and Operator Notes

- The application and packaging version is `1.10.11`.
- P2P, local database, and trade protocol version numbers remain unchanged. No protobuf definition changed.
- netlayer is replaced by the Tor library of the `tor` submodule. The key of the onion service stays in
  `tor/hiddenservice` in netlayer's format, so a node keeps its onion address.
- The first start moves the state of Tor into `tor/data/`. `tor/hiddenservice/` and `tor/external_tor.config` stay
  where they are.
- Bisq brings its own Tor on every operating system, as 1.10.10 did on Linux x86_64, macOS and Windows, and does not
  use the `tor` package of the distribution; the deb and rpm packages do not depend on it. Linux ARM64 gets the Tor of
  the alpha release 16.0a14 of Tor Browser, with the same tor 0.4.9.14. On Linux, Tor needs glibc 2.17 (2.27 on ARM64)
  and `libz.so.1` of the system.
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
- `ClientTransportPlugin` and `ServerTransportPlugin` entries of `--torrcOptions` or `--torrcFile` in
  `bisq.properties` stop the start; they are accepted on the command line only.
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
- The Docker deployment of the seed node in `seednode/deployment_v2/docker` is not supported with the Tor library.
- Release builds use Azul Zulu 21.0.13 and JavaFX 21.0.13.

## Notable Changes

### Tor Library Instead of netlayer

P2P connections, the onion service and the SOCKS proxy for Bitcoin and HTTP requests now go through `TorService` of
the Tor library, which is a Git submodule of the repository and which Bisq 2 also uses. netlayer, tor-binary, the
JitPack builds of jsocks and jtorctl and kotlin-logging-jvm are removed, with their verification metadata.

The key of the onion service stays in `tor/hiddenservice/private_key` in the format which netlayer wrote. Bisq derives
the onion address from the key, as Tor does, and rewrites a missing or wrong `hostname`. A generated key is written in
netlayer's format, so that an earlier version can read it.

On every operating system, Bisq runs the Tor of the Tor expert bundle of Tor Browser 15.0.25 (tor 0.4.9.14), whose
PGP signature, SHA-256 hash and tor version the build verifies, the same Tor version as netlayer brought in 1.10.10.
Linux ARM64, for which netlayer had no Tor, gets the bundle of the alpha release 16.0a14 with the same tor, because no
stable release has a bundle for Linux ARM64. The bundles bring the pluggable transports `lyrebird` and
`conjure-client`. Bisq uses neither a `tor` nor transports on the `PATH`, and the Linux packages do not depend on the
`tor` package. On Linux, Tor needs glibc 2.17 (2.27 on ARM64) and `libz.so.1` of the system. The bundled Tor gets its
GeoIP files and an empty OpenSSL configuration in the Tor directory instead of the paths below `/var/tmp/dist` which
its binary has compiled in.

The Tor directory gets the parts `bin/` (the bundle), `data/` (the data directory of Tor) and `sandy/` (the sandbox
program). If Tor does not bootstrap within 4 minutes, Bisq deletes the Tor files except `hiddenservice/` and
`external_tor.config` and starts Tor again, up to 5 starts, as netlayer did. This deletion, and "Delete outdated Tor
files and shut down" in the Tor network settings, no longer follow symbolic links or junctions.

At the shutdown, the node waits up to 8 seconds for the close of its connections and the stop of its Tor. On Linux, Tor
runs in a session of its own, so a Ctrl+C in the terminal of the node reaches only the node, whose shutdown still uses
Tor and stops it at its end.

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

Every Bisq application runs the Tor which it starts in the sandbox of sandy 2.4.1, which every build contains with its
pinned hash, also the seed node, the BTC node monitor and the inventory monitor. Tor may read its torrc and the bundle
and write only its data directory, has a network of its own, and cannot start other processes or signal processes
outside the sandbox. Where the system cannot enforce this, sandy uses a weaker level, which the log names with a
warning; the desktop application also shows a notice for a level with the network of the computer or without isolated
signals, which says what the level gives up and when Tor gets the stronger protection, until the user chooses "Don't
show again" for that combination of weaknesses. If no level can be enforced, the start fails with an error which
names the reason and `--useTorSandbox=false`; Bisq never starts Tor without the sandbox instead. The inventory monitor,
which takes positional arguments, turns the sandbox off with `0` as its seventh argument, and its error names that
argument. A running Tor runs without the sandbox of the node.

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
start. `ClientTransportPlugin` and `ServerTransportPlugin` entries make Tor start a program; they are accepted only if
the command line sets the option which brings them, because whoever can change `bisq.properties` could otherwise run a
program at the next start.

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

The Docker deployment in `seednode/deployment_v2/docker` is not supported: it passes `--torControlHost` and
`--torControlPassword`, and its Tor runs in another container, which cannot reach the onion service of the node.

### Build Tooling

Release builds use Azul Zulu 21.0.13, and JavaFX is updated to 21.0.13. Linux ARM builds use the linux-aarch64 JavaFX
artifacts, which JavaFX publishes since 21.0.11. The dependency verification instructions follow those of Bisq 2, and
the Tor upgrade instructions describe the upgrade through the `tor` submodule.

## Known Issues

- On Linux ARM64, Bisq runs the Tor of the alpha release 16.0a14 of Tor Browser, because no stable release has a Tor
  expert bundle for Linux ARM64. It contains the same tor 0.4.9.14 as the stable bundle of the other platforms.
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
backup of the data directory (`docs/specifications/application/data-directory-backup.md`). `docs/tor-upgrade.md`
describes the upgrade of Tor through the `tor` submodule and the pins of the bundles, `docs/build.md` and the known
installation issues say that Bisq brings its own Tor on Linux and what it needs from the system, and
`seednode/README.md` describes the seed node with its own Tor in the sandbox and with a running Tor. Unit tests cover
the key formats, the bridge settings, the start failures, the torrc rules, the HTTP isolation, the backup and the
shutdown. This release note summarizes source changes; it does not claim that release builds or tests were run for
this note.

## Commit Summary

| Commit | Summary |
| --- | --- |
| `d548269649` | Refactoring: Remove the unused Tor download build plugins |
| `efe7e9057b` | Parse the torrc options in Bisq instead of netlayer |
| `e4b126bb21` | Keep external_tor.config when the Tor files get deleted |
| `323d2f7eb5` | Add the Tor library as the Git submodule tor |
| `a41734fe77` | Use the Tor library instead of netlayer |
| `c908dcb5ee` | Stop Tor and close the server socket without the Tor worker |
| `223559db3e` | Depend on the tor package on Linux |
| `a9c16c4698` | Remove netlayer |
| `e9fda976fc` | Specify the onion service identity and the Tor startup |
| `3da5557d97` | Use relative URLs for the local tor repository |
| `8247b7d550` | Read the onion service key in the format which netlayer wrote |
| `46d1993aaa` | Update the Tor library to its bug fixes |
| `058f92dbad` | Leave the stop of a failed Tor start to the Tor library |
| `08ee1b174c` | Let a second shutdown of the Tor node join the running shutdown |
| `4fd3b7b2e1` | Update the Tor library to its hardening |
| `8fc928c0cf` | Leave the protected torrc keys to the Tor library |
| `691ed26938` | Delete the Tor files without following symbolic links |
| `bd644fbf2b` | Use SAFECOOKIE and fail closed with a running Tor |
| `3d4446ae72` | Show why a running Tor cannot be used when the start fails |
| `5ddf0996e1` | Let a running Tor be used with a password through external_tor.config |
| `a08d10ca12` | Update the Tor library to its bridges, Whonix and Android fixes |
| `42592f6d8a` | Use the bridges of the Tor network settings with the Tor library |
| `eedd43ad32` | Open the Tor network settings again when Tor cannot connect |
| `4847ea314c` | Ignore the Tor options of the Whonix guide on Whonix |
| `c5d5ce01dd` | Update the Tor library to its sandbox integration |
| `7f96575583` | Delete the Tor files without entering junctions |
| `1445620f06` | Skip the Tor data directory in the backup |
| `86250b94e1` | Let Bisq 1 run its Tor in the sandbox with --useTorSandbox |
| `0d019a8878` | fix(p2p): fail at once when a running Tor does not bootstrap in time |
| `6149c463db` | docs(p2p): move the javadoc of deleteTorFiles to the method |
| `a5510fdbb5` | Document local Tor submodule configuration |
| `24ee4bc12e` | Use relative path for tor submodule for dev purpose |
| `88c3ad4890` | Set default value for USE_TOR_SANDBOX to true |
| `7ecc5c959a` | Apply temp fix for latest tor/sandy version |
| `01a0d05444` | build(tor): move the tor submodule to the packaged sandy 2.2.1 |
| `968bfbc804` | feat(tor): add an option and a switch for pluggable transports in the sandbox |
| `86485284ca` | docs(gradle): adopt the Bisq 2 dependency verification instructions |
| `a6b23b2877` | build: update the pinned Java version to Azul Zulu 21.0.13 |
| `a4560e0634` | build(deps): update JavaFX to 21.0.13 |
| `2f115af01f` | docs(gradle): describe the JavaFX verification metadata update |
| `9579d16b61` | fix(build): use the linux-aarch64 JavaFX classifier on Linux ARM |
| `2303377050` | Set version 1.10.11 |
| `b7da02fcec` | docs(tor): raise the version of the tor artifacts in a Tor upgrade |
| `f745748eea` | fix(i18n): give the changed bridge line prompt a new key |
| `2ceeb89841` | fix(desktop): add the Tor log to the zip of the log files |
| `a976de46c6` | fix(p2p): wait for the bounded stop of Tor at the shutdown |
| `94d8a5d0dd` | fix(desktop): disable the pluggable transports switch for a command line option |
| `8d78168dc8` | fix(desktop): show an invalid option at the start in a dialog |
| `a7d7a99070` | fix(common): do not follow links in the backup of the data directory |
| `2037610166` | fix(common): make the backup of the data directory owner-only |
| `2833ceb168` | fix(p2p): accept torrc entries which start a program only from the command line |
| `14e25d7bb7` | fix(p2p): send the stream isolation credentials with HTTP requests |
| `5a96d265d7` | fix(tor): explain a refusal of the transports of the bridges in Bisq 1 terms |
| `42f55991bb` | fix(tor): keep a Ctrl+C in the terminal of the node away from its Tor |
| `f71b6d131b` | fix(p2p): send the stream isolation credentials with HTTP requests |
| `d0d99260d0` | fix(seednode): let only the node stop its own Tor at a systemd stop |
| `456d1fb0ef` | fix(core): exit with the failure status when a headless node cannot start Tor |
| `c38b3cb982` | docs(tor): name external_tor.config as a file to keep in the Tor directory |
| `c9d0acddfe` | docs(tor): say that the tor package starts the Tor service of the system |

TODO (maintainer): add the later commits of `fix-open-items-after-crosscheck` and `linux-bundled-tor-sandbox-defaults`
and the other commits of the release.
