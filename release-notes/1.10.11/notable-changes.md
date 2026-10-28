# Bisq 1.10.11 Notable Changes

> **Draft. The maintainer completes it before the release** (see the list at the top of `full-release-notes.md`):
> it covers only the switch from netlayer to the Tor library and the changes that came with it. The commits are named
> by their hashes on `master_henrik_private`, `fix-open-items-after-crosscheck` and
> `linux-bundled-tor-sandbox-defaults`; link them to `https://github.com/bisq-network/bisq/commit/<hash>` once they are
> there.

Bisq 1.10.11 replaces netlayer with the Tor library which Bisq 2 also uses, runs the Tor which it starts in a sandbox
by default, and changes how a running Tor, bridges, Tor's logs and the backup work.

## Tor Library Instead of netlayer

- P2P connections, the onion service and the SOCKS proxy for Bitcoin and HTTP requests go through the Tor library of
  the `tor` submodule. netlayer, tor-binary and their dependencies are removed.
- The key of the onion service stays in `tor/hiddenservice` in the format which netlayer wrote, and the onion address
  is derived from it, so a node keeps its onion address. A generated key is also written in netlayer's format.
- On every operating system, Bisq runs the Tor of the Tor expert bundle (Tor Browser 15.0.25, tor 0.4.9.14), whose PGP
  signature, SHA-256 hash and tor version the build verifies. Linux ARM64 gets the bundle of the alpha release 16.0a14
  with the same tor, because no stable release has a bundle for Linux ARM64. The bundles bring the pluggable transports
  `lyrebird` and `conjure-client`, also on Linux ARM64.
- Bisq uses neither a `tor` nor transports on the `PATH`, and the deb and rpm packages do not depend on the `tor`
  package. On Linux, Tor needs glibc 2.17 (2.27 on ARM64) and `libz.so.1` of the system.
- The Tor directory gets the parts `bin/`, `data/` and `sandy/`. The first start moves the state of Tor into `data/`;
  `hiddenservice/` and `external_tor.config` stay where they are.
- If Tor does not bootstrap within 4 minutes, Bisq deletes the Tor files except `hiddenservice/` and
  `external_tor.config` and starts Tor again, up to 5 starts, as netlayer did. The deletion does not follow links.
- At the shutdown, the node waits up to 8 seconds for the stop of its Tor. On Linux, a Ctrl+C in the terminal of the
  node no longer ends Tor at once, so that the shutdown can still use Tor.

Related commits: `d548269649`, `efe7e9057b`, `e4b126bb21`, `323d2f7eb5`, `a41734fe77`, `c908dcb5ee`, `223559db3e`,
`a9c16c4698`, `e9fda976fc`, `8247b7d550`, `46d1993aaa`, `058f92dbad`, `08ee1b174c`, `4fd3b7b2e1`, `691ed26938`,
`c5d5ce01dd`, `7f96575583`, `a976de46c6`, `42f55991bb`, `c38b3cb982`, `c9d0acddfe`, `7e5ada5246`, `12627d7680`,
`5dd656d729`.

## Running Tor

- A running Tor is configured in `external_tor.config` in the Tor directory (`UseExternalTor 1`, `ControlPort`, and
  `CookieAuthentication 1` with `CookieAuthFile`, or `ControlPassword`). `TOR_SKIP_LAUNCH` and Whonix also select a
  running Tor.
- With a cookie, Bisq authenticates with SAFECOOKIE: Tor proves that it knows the cookie before Bisq sends anything.
- If the running Tor cannot be used, or does not bootstrap within 4 minutes, the start fails with an error which names
  the host, the port and the cause. Bisq never starts its own Tor instead.
- The options `--torControlHost`, `--torControlPort`, `--torControlPassword`, `--torControlCookieFile` and
  `--torControlUseSafeCookieAuth` stop the start with an explanation which names `external_tor.config` and the config
  file, also in `bisq.properties`. The desktop application shows the explanation in a dialog. On Whonix, Bisq ignores
  them with a warning, because it always uses the Tor of the Whonix-Gateway there.
- Bisq does not start on a Whonix-Gateway; the error says to run it on a Whonix-Workstation.
- Bisq connects to the SOCKS listener which the running Tor reports, at its address, or at the host of the control
  port for a listener on all addresses. A Tor on another host which listens for SOCKS only on its loopback address is
  refused with an error which says what to add.
- A running Tor whose SOCKS port does not isolate streams by their SOCKS credentials (`NoIsolateSOCKSAuth`) is
  refused, because Bisq keeps its connections apart with them. On Whonix, Bisq cannot read this configuration and
  logs that it could not check it.
- Bisq connects to the control port again only while the connection is refused: a host which does not answer fails
  the start after 10 seconds, an unreachable host at once.

Related commits: `a41734fe77`, `bd644fbf2b`, `3d4446ae72`, `5ddf0996e1`, `4847ea314c`, `0d019a8878`, `8d78168dc8`.

## Bridges

- The node reads the choice of the Tor network settings at every start. The provided bridges are the built-in bridges
  of the Tor Browser version of the bundled Tor; the old lists of about 2019 are no longer used.
- The bridge types are obfs4, snowflake and meek. A stored obfs3 choice becomes snowflake, and meek-amazon and
  meek-azure become meek.
- If the chosen bridges cannot be used, the start fails; Bisq never connects without them.
- When Tor cannot connect, the desktop application opens the Tor network settings again, as with netlayer.
- Bridge lines are no longer written to the log.
- The prompt of the custom bridges field shows an example bridge line under a new translation key.
- For a downgrade, the settings still store the bridge lines of the choice, which earlier versions read. For provided
  bridges these lines come from the installed Tor bundle, so they are empty if the node has not installed one, for
  example with a running Tor; an earlier version then starts Tor without bridges.

Related commits: `42592f6d8a`, `eedd43ad32`, `f745748eea`.

## Tor Sandbox

- Every Bisq application runs the Tor which it starts in the sandbox of sandy 2.4.1, which every build contains: the
  desktop application, the daemon, the seed, statistics, bridge and REST nodes, the BTC node monitor and the inventory
  monitor. `--useTorSandbox=false` turns it off; the inventory monitor, which takes positional arguments, takes `0` as
  its seventh argument instead. A running Tor runs without it.
- Where the system cannot enforce the full sandbox, sandy uses a weaker level, which the log names with a warning. If
  no level can be enforced, the start fails with an error which names the reason and the option; Bisq never starts
  Tor without the sandbox instead.
- When Tor runs in a level with the network of the computer or without isolated signals, the desktop application
  shows a notice after the start, which says what the level gives up and when Tor gets the stronger protection.
  "Don't show again" hides it for that combination of weaknesses.
- Bridges with pluggable transports need `--allowPluggableTransportsInTorSandbox=true`. The Tor network settings have
  the switch "Allow pluggable transports in the Tor sandbox" for it, which takes effect after a restart. The switch is
  disabled while the sandbox is off, on Windows, and when the command line sets the option.
- When the transports of the chosen bridges are not allowed, the error names them and the switch, and the Tor network
  settings open. On Windows, Tor cannot start pluggable transports in the sandbox.
- On macOS, only webtunnel, snowflake and conjure bridges let the sandbox give Tor and its transports the certificate
  verifier of the system, which they need; obfs4 and meek bridges do not get it.
- An error advises to turn the sandbox off only if sandy refused to run Tor, which Bisq reads from a status record that
  Tor cannot write, so that a compromised Tor cannot talk the user into it. When sandy refuses before Tor runs, for
  example over a port which another process took, Bisq starts Tor once more with new ports. The quoted standard error
  of sandy and Tor comes last in the error and is labeled as not verified by Bisq.
- The desktop application shows the refusals of the sandbox, of a running Tor and of a Whonix-Gateway without its
  generic advice to check the internet connection or to restart, because their errors say what helps.
- When Tor runs without the sandbox after it ran in it, Bisq clears the data directory of Tor first, so that what a
  compromised sandboxed Tor wrote there is not read with all rights of the user. Tor then selects new entry guards and
  downloads the directory again.

Related commits: `86250b94e1`, `88c3ad4890`, `7ecc5c959a`, `01a0d05444`, `968bfbc804`, `94d8a5d0dd`, `5a96d265d7`,
`805747f207`, `40942e58b6`, `f133f79fb9`.

## Torrc Options

- `--torrcFile` and `--torrcOptions` still add torrc entries. Entries for `DataDirectory`, `ControlPort`,
  `ControlPortWriteToFile`, `HashedControlPassword`, `SocksPort` and `DisableNetwork` are ignored with a warning, and
  an entry with a line break stops the start.
- Entries which would give another program control over Tor are ignored with a warning as well: `ControlSocket`,
  `ControlSocketsGroupWritable`, `CookieAuthentication`, `CookieAuthFile`, `CookieAuthFileGroupReadable`,
  `__HashedControlSessionPassword` and `__OwningControllerFD`.
- `ClientTransportPlugin` and `ServerTransportPlugin` entries are accepted only if the command line sets the option
  which brings them. From `bisq.properties`, or from a torrc file which it names, they stop the start.
- An inline `# comment` after a value in the file of `--torrcFile` now stops the start; netlayer accepted it.

Related commits: `efe7e9057b`, `4fd3b7b2e1`, `8fc928c0cf`, `2833ceb168`.

## Stream Isolation of HTTP Requests

- With `--torStreamIsolation`, each HTTP request through Bisq's Tor sends new SOCKS credentials, so that Tor puts it on
  a circuit of its own. Before, all HTTP requests shared one isolation group, also with the option.

Related commit: `14e25d7bb7`.

## Logs and Backup

- Tor writes its log to `tor/tor-stdout.log` and `tor/tor-stderr.log` instead of the Bisq log. "Zip Log files" adds
  both files and the earlier log `tor/tor-stdout.log.1`. The control traffic of Tor is logged at debug level only,
  because it contains onion addresses.
- "Backup now" skips the data directory of Tor and its record `tor/data-mode`, does not follow symbolic links or
  junctions, and names the entries which it did not copy. On file systems with POSIX permissions, only the owner can
  read the backup.
- The private keys of the node in `keys/` and their rolling backups are readable by the owner only on file systems with
  POSIX permissions, also those of earlier versions from the next start on. Before, every local user who could enter
  the data directory could read them.

Related commits: `c5d5ce01dd`, `4fd3b7b2e1`, `2ceeb89841`, `1445620f06`, `a7d7a99070`, `2037610166`.

## Seed Nodes and Other Headless Nodes

- Seed, statistics, bridge and REST nodes and the inventory monitor shut down with exit status 1 when Tor or the onion
  service cannot start, so that systemd or the start script restarts them. Before, they kept running without the P2P
  network.
- The BTC node monitor exits with status 1 when its Tor cannot start, and logs the reason. Before, it kept its HTTP
  server running without Tor and wrote the reason only to the standard error.
- The seed node starts its own Tor and runs it in the sandbox instead of the tor service of the system: `bisq.env`
  sets `BISQ_EXTERNAL_TOR_PORT=-1`, and the unit no longer starts after `tor@default`. The install script installs
  the `tor` package only for Bitcoin. For a running Tor, the unit writes `external_tor.config` from `bisq.env`, and
  `seednode/README.md` describes the setup. The unit sends SIGTERM only to the node (`KillMode=mixed`), which stops its
  own Tor at the end of its shutdown. A stop no longer leaves the unit failed: the unit takes the exit status 143, with
  which the JVM ends after SIGTERM, as a clean stop (`SuccessExitStatus=143`).
- Seed nodes run the Tor of the Bisq build in the sandbox. An installed `/etc/default/bisq.env` keeps
  `BISQ_EXTERNAL_TOR_PORT=9051`, so operators switch an upgraded seed node by setting `-1` there and removing `bisq`
  from the group `debian-tor`; the onion address stays the same. Only where the system cannot enforce the sandbox does
  a seed node keep the tor service, with the drop-in of `seednode/README.md`, because the new unit no longer starts
  after `tor@default`.
- Seed node operators install the new unit and reload systemd before they restart the upgraded node, as
  `seednode/README.md` describes. The unit of earlier versions passes `--torControl*` options, which now make the node
  exit at every start.
- The install script installs `/etc/default/bisq.env` and `/bitcoin/bitcoin.conf` with mode 600 instead of 644, so
  that other local users cannot read the Bitcoin RPC password and the private key of the Burning Man oracle. Operators
  of existing installations run `sudo chmod 600 /etc/default/bisq.env /bitcoin/bitcoin.conf`. The new unit passes
  both secrets through `bisq.properties` in the data directory of the node (mode 600) instead of the command line,
  which every local user can read.
- The install script creates `/bisq`, the home of the user `bisq` with the data directory of the node, with mode 700,
  because the node writes its wallets and logs readable by every user who can enter the directory. Operators of
  existing installations run `sudo chmod 700 /bisq`.
- Monitored seed nodes set `seedNodeReportingServerUrl=http://localhost:13003` in `bisq.properties` of the data
  directory: the patch of bisq-monitor's install script, which added the option to the old unit, does not apply to
  the new one.
- The Docker deployment in `seednode/deployment_v2/docker` is not supported with the Tor library.

Related commits: `a41734fe77`, `bd644fbf2b`, `5ddf0996e1`, `d0d99260d0`, `456d1fb0ef`, `545a3aeac6`.

## Build Tooling

- Release builds use Azul Zulu 21.0.13. JavaFX is updated to 21.0.13, and Linux ARM builds use the linux-aarch64
  JavaFX artifacts.
- The dependency verification instructions follow Bisq 2, and the Tor upgrade instructions name the version of the
  tor artifacts.

Related commits: `a6b23b2877`, `a4560e0634`, `2f115af01f`, `9579d16b61`, `86485284ca`, `b7da02fcec`.

## Version

The application and packaging version is `1.10.11`, set by `2303377050`.
