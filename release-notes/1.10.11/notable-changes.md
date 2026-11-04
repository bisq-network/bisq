# Bisq 1.10.11 Notable Changes

> **Draft. The maintainer completes it before the release** (see the list at the top of `full-release-notes.md`):
> it covers `release-candidate-v1.10.11` through `e6925cd038`. The commits are named by their hashes on that branch;
> link them to `https://github.com/bisq-network/bisq/commit/<hash>`.

Bisq 1.10.11 replaces netlayer with the Tor library which Bisq 2 also uses, runs the Tor which it starts in a sandbox
by default, and changes how a running Tor, bridges, Tor's logs and the backup work. It also rejects malformed network
data which stopped the start of nodes.

## Tor Library Instead of netlayer

- P2P connections, the onion service and the SOCKS proxy for Bitcoin and HTTP requests go through the Tor library of
  the `tor` submodule. netlayer, tor-binary and their dependencies are removed.
- The key of the onion service stays in `tor/hiddenservice` in the format which netlayer wrote, and the onion address
  is derived from it, so a node keeps its onion address. A generated key is also written in netlayer's format.
- On every operating system, Linux ARM64 included, Bisq runs the Tor of the Tor expert bundle (Tor Browser 16.0,
  tor 0.4.9.15), whose PGP signature, SHA-256 hash and tor version the build verifies. The bundles bring the pluggable
  transports `lyrebird` and `conjure-client`.
- Bisq uses neither a `tor` nor transports on the `PATH`, and the deb and rpm packages do not depend on the `tor`
  package. On Linux, Tor needs glibc 2.17 (2.27 on ARM64) and `libz.so.1` of the system.
- The Tor directory gets the parts `bin/`, `data/` and `sandy/`. The first start moves the state of Tor into `data/`;
  `hiddenservice/` and `external_tor.config` stay where they are.
- If Tor does not bootstrap within 4 minutes, Bisq deletes the Tor files except `hiddenservice/` and
  `external_tor.config` and starts Tor again, up to 5 starts, as netlayer did. The deletion does not follow links.
- At the shutdown, the node waits up to 8 seconds for the stop of its Tor, and so does the BTC node monitor. The Tor
  library also stops the processes which Tor started; processes which it could not stop are reported and stopped
  again at the next start. On Linux, a Ctrl+C in the terminal of the node no longer ends Tor at once, so that the
  shutdown can still use Tor.

Related commits: `07af1b95e0`, `25075eeea2`, `44d141b7f8`, `6d09422416`, `ecb30afaef`, `1b73ac43f1`, `2dd4d2ff87`,
`cb7457788a`, `d08e48470f`, `bea4f7fee8`, `eb3ab4b48c`, `6d6b90fe0d`, `ded4678005`, `b786a8c197`, `493f2e50c9`,
`2723964e89`, `692f5f6980`, `c10b178876`, `e089c49b36`, `b6095e7b53`.

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

Related commits: `ecb30afaef`, `493f2e50c9`, `01bac25c96`, `ea1bdabf23`, `6409e87026`.

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

Related commits: `dfbcc99cfb`.

## Tor Sandbox

- Every Bisq application runs the Tor which it starts in the sandbox of sandy 1.0.0, which every build contains: the
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
- The build checks each sandy archive against its pinned hash and against the signed checksums of the sandy release.

Related commits: `b786a8c197`, `f3cf0f2977`, `0685dfd0ea`, `74c3de393b`, `cf0706ea7e`, `6cedf0b411`, `ed37679193`,
`250efb31b0`, `2517a40332`, `125a6746c7`, `b6095e7b53`.

## Torrc Options

- `--torrcFile` and `--torrcOptions` still add torrc entries. Entries for `DataDirectory`, `ControlPort`,
  `ControlPortWriteToFile`, `HashedControlPassword`, `SocksPort` and `DisableNetwork` are ignored with a warning, and
  an entry with a line break stops the start.
- Entries which would give another program control over Tor are ignored with a warning as well: `ControlSocket`,
  `ControlSocketsGroupWritable`, `CookieAuthentication`, `CookieAuthFile`, `CookieAuthFileGroupReadable`,
  `__HashedControlSessionPassword` and `__OwningControllerFD`.
- `ClientTransportPlugin`, `ServerTransportPlugin`, `AccelName` and `AccelDir` entries are accepted only if the command
  line sets the option which brings them. From `bisq.properties`, or from a torrc file which it names, they stop the
  start of the Tor which Bisq starts. With bridges, Bisq ignores `ClientTransportPlugin` entries, so they do not stop
  the start.
- An inline `# comment` after a value in the file of `--torrcFile` now stops the start; netlayer accepted it.

Related commits: `25075eeea2`, `6d6b90fe0d`, `6e39623c57`, `7ebf727ff8`, `dff18fbe2f`, `2f6380449f`.

## Stream Isolation of HTTP Requests

- With `--torStreamIsolation`, each HTTP request through Bisq's Tor sends new SOCKS credentials, so that Tor puts it on
  a circuit of its own. Before, all HTTP requests shared one isolation group, also with the option.

Related commit: `3a21bec4ee`.

## Logs and Backup

- Tor writes its log to `tor/tor-stdout.log` and `tor/tor-stderr.log` instead of the Bisq log. "Zip Log files" adds
  both files and the earlier log `tor/tor-stdout.log.1`. The control traffic of Tor is logged at debug level only,
  because it contains onion addresses.
- The zip leaves out the relays and bridges which the logs name: in the logs of Tor their fingerprints, nicknames,
  identities and addresses, also IPv6 addresses which end with `::`, and the URLs and host names in the messages of the
  pluggable transports, also under a Punycode top level domain; in the Bisq log
  the relay or bridge of a bootstrap warning. A privately obtained bridge is blocked once it is known. The files on
  disk keep everything, so share the zip, not the files.
- "Backup now" skips the data directory of Tor and its record `tor/data-mode`, does not follow symbolic links or
  junctions, and names the entries which it did not copy. On file systems with POSIX permissions, only the owner can
  read the backup.
- The private keys of the node in `keys/` and their rolling backups are readable by the owner only on file systems with
  POSIX permissions, also those of earlier versions from the next start on. Before, every local user who could enter
  the data directory could read them.

Related commits: `153eb61a36`, `d07e7da848`, `8b694e5e59`, `2de5f5d393`, `94fb6c7ec8`, `9163bf852e`, `8b8aaa5f38`,
`4334c3eea9`.

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
- The Docker deployment in `seednode/deployment_v2/docker` runs Tor in the container of the seed node instead of the
  service `bisq-tor`, which could not reach the onion service of the node. Operators remove the stack and deploy it
  again, as the README of the seed node describes.
- On a Turkish default locale, enum options such as `--baseCurrencyNetwork=btc_mainnet`, values of the daemon API and
  the CLI, and the network files of the two monitors are found again. `bisq.service` derives the network directory as
  the node does.

Related commits: `74e439ccb2`, `35e470260b`, `cbbfffa320`, `f3cf0f2977`, `21dfda8edd`, `39c94e0e67`, `8498d3bb35`,
`125a6746c7`, `df4cd7481b`, `0bb1e71510`, `65fb5a794b`, `893ae54274`, `463fd31b77`, `bb230593b4`, `697240b24a`.

## Validation of Network Data

- A signed witness with a malformed signer key or signature has an invalid signature instead of breaking the sign
  state and trade limit checks of the account. A witness with an empty account hash or key, or an unknown verification
  method, is no longer stored; such a witness stopped the start of every node which received it. A stored one is
  removed when the store is read.
- A copy of a signed witness with another owner key, made from another encoding of the same signature and key, gets
  the hash of the original and is no longer stored next to it. A witness of a mainnet arbitrator key dated 2021-02-01
  or later is invalid.
- A malformed dispute agent registration no longer stops the start of the node or empties the agent registry. An
  agent published with the address of a registered agent can no longer remove that agent from the registry.
- A malformed signature of a private notification no longer skips the other mailbox messages at the start, and an
  invalid notification no longer makes the shown one return.
- Bisq opens only `http` and `https` links, also from popup texts and DAO data.

Related commits: `cce4f07996`, `b245ddbb70`, `44526cc255`, `ce1707da39`, `b58c82efcb`, `0380d51d2c`, `43c21132a4`,
`690559297d`, `d0fb0ce052`, `5f2c7a38f4`.

## Build Tooling

- Release builds use Azul Zulu 21.0.13. JavaFX is updated to 21.0.13, and Linux ARM builds use the linux-aarch64
  JavaFX artifacts. The release build check compares the Java update by its version numbers and the runtime build
  exactly.
- The dependency audit and report cover the included `tor` build. The sandy 1.0.0 archives are reviewed
  checksum-only entries.
- The dependency verification instructions follow Bisq 2, and the Tor upgrade instructions name the version of the
  tor artifacts.

Related commits: `15224159c5`, `043aa0164f`, `a4c13d84d3`, `ace34126c9`, `99aa8c448a`, `f3874247d4`, `b373b1e3d5`,
`8b852db86f`, `144622ace3`, `376ed4a0ff`, `fd7df3cd3c`.

## Version

The application and packaging version is `1.10.11`, set by `546c81b1e6`.
