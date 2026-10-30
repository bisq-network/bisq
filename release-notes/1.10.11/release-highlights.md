# Bisq 1.10.11 Highlights

> **Draft. The maintainer completes it before the release** (see the list at the top of `full-release-notes.md`):
> it covers only the switch from netlayer to the Tor library and the changes that came with it. Add the other
> changes of the release, confirm the version number and set the dates.

## Tor

- Bisq uses the Tor library, which Bisq 2 also uses, instead of netlayer. Nodes keep their onion address.
- Bisq brings its own Tor on every operating system: tor 0.4.9.14 of Tor Browser 15.0.25, as 1.10.10 did on Linux
  x86_64, macOS and Windows. Linux ARM64 gets the same tor, with its pluggable transports, from the alpha release
  16.0a14, because no stable release has a Linux ARM64 bundle yet. Bisq does not use the `tor` package of the
  distribution.
- Every Bisq application runs the Tor which it starts in a sandbox (sandy 2.4.1) by default, also the seed node, the BTC
  node monitor and the inventory monitor. `--useTorSandbox=false` turns it off; the inventory monitor takes `0` as its
  seventh argument.
- Bridges with pluggable transports need the new switch "Allow pluggable transports in the Tor sandbox" of the Tor
  network settings. On Windows they need the sandbox off.
- The provided bridges are the current built-in bridges of Tor Browser: obfs4, snowflake and meek. obfs3, meek-amazon
  and meek-azure are gone.
- A running Tor is configured in `external_tor.config` in the Tor directory. The `--torControl*` options stop the
  start, except on Whonix. Bisq runs on a Whonix-Workstation, not on the Whonix-Gateway.
- With `--torStreamIsolation`, each HTTP request gets a Tor circuit of its own.

## Privacy and Security

- "Backup now" creates owner-only files and does not follow links.
- The private keys of the node and their backups are owner-only on Linux and macOS.
- Torrc entries which make Tor start a program are accepted only from the command line, and entries which would give
  another program control over Tor are ignored.
- A running Tor whose SOCKS port does not isolate streams by their SOCKS credentials is refused.
- When the Tor sandbox is turned off, Bisq clears the data of Tor which a sandboxed Tor wrote. Only a refusal of sandy,
  which Tor cannot fake, makes Bisq advise to turn the sandbox off.
- "Zip Log files" adds the logs of Tor.

## Operators

- Seed node operators install the new `bisq.service` before they restart the upgraded node; the old unit makes it
  exit at every start.
- Seed nodes run the Tor of the Bisq build in the sandbox instead of the tor service of the system. Operators switch
  upgraded seed nodes with `BISQ_EXTERNAL_TOR_PORT=-1` in `/etc/default/bisq.env` (`seednode/README.md`).
- Seed, statistics, bridge and REST nodes, the inventory monitor and the BTC node monitor exit with a failure status
  when Tor cannot start, so that systemd or their start script can restart them.
- The seed node unit lets the node stop its own Tor.
- Seed node operators run `sudo chmod 600 /etc/default/bisq.env /bitcoin/bitcoin.conf`: earlier install scripts left
  the RPC password and the key of the Burning Man oracle readable by every local user. The new unit also keeps both
  off the command line of the node.
- Seed node operators run `sudo chmod 700 /bisq`, so that other local users cannot read the wallets and logs of the
  node.
- Monitored seed nodes set `seedNodeReportingServerUrl` in `bisq.properties`, because the patch of the monitoring
  script does not apply to the new unit.

## Builds

- Release builds use Azul Zulu 21.0.13 and JavaFX 21.0.13.
- The application and packaging version is `1.10.11`.
