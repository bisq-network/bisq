# Tor startup and configuration

## Scope

This specification covers how a Bisq node starts Tor or connects to a running Tor, which user settings apply, and how
the start fails. The key of the onion service is covered by
[`onion-service-identity.md`](onion-service-identity.md).

## Required behaviour

### Tor of the node

On macOS and Windows, the node runs the Tor of the Tor expert bundle which the Bisq build downloaded and whose PGP
signature it verified. On Linux, the node runs the `tor` executable of the system, found on the `PATH`; the Linux
packages of Bisq depend on the `tor` package. If no `tor` executable is found, the start fails with an error which says
that Tor is not installed.

The node owns the Tor it starts. Tor gets the process ID of the node as its owning controller and stops when the node
stops. If the start fails after Tor was launched, including the publication of the onion service, the node stops that
Tor, so that no node remains which reaches peers but cannot be reached. A shutdown during the start also stops the
Tor which the start launches. When the start of Tor or of the onion service fails, the seed, statistics, bridge and
REST nodes shut down with the failure exit status, so that systemd or their start script starts them again, instead of
staying unreachable until their periodic restart. At the shutdown, the node waits for the stop of its Tor: the Tor
library asks Tor to exit and kills it after 3 seconds, which takes at most about 6 seconds, and the node waits at most
8 seconds for the close of its connections and the stop of Tor.

The node starts its Tor in a session of its own with the `setsid` program of the system, if the
system has one, as Linux does. A Ctrl+C in the terminal which started the node then reaches only the node, whose
shutdown still uses Tor, for example to remove the offers of the user, and stops Tor at its end. macOS has no `setsid`:
there Tor stays in the process group of the node, and a Ctrl+C in the terminal ends Tor at once. For the same reason,
the systemd unit of the seed node sends the stop signal only to the node; systemd kills the processes which are left
after the node exited, or after the stop timeout.

If Tor does not bootstrap within 4 minutes, the node stops it, deletes the files in the Tor directory except the
`hiddenservice` directory and `external_tor.config`, and starts Tor again, up to 5 starts. Tor sometimes cannot
bootstrap with the files of an earlier run; netlayer retried the same way.

The node waits for the upload of its onion service descriptor without a time limit, as netlayer did, because Tor
keeps trying to upload it.

Tor must not read a configuration file outside the Tor directory of the node. The node therefore also passes the torrc
which it generates as the defaults torrc, instead of the built-in default location. The bundled Tor of macOS and
Windows has the paths of its build below `/var/tmp/dist` compiled in, which other local users can create. The node
therefore sets the GeoIP files of the bundle in the torrc, and points `OPENSSL_CONF` to an empty file in the Tor
directory, because OpenSSL would otherwise read its configuration there and could load modules from it.

The commands, replies and events of the control connection are logged at debug level only, because they contain onion
addresses.

### Running Tor

The node uses a running Tor instead of its own if `external_tor.config` in its Tor directory sets `UseExternalTor 1`,
if the environment variable `TOR_SKIP_LAUNCH` is `1`, `true` or `yes`, or if it runs on Whonix. `external_tor.config`
sets the control port and whether a cookie authenticates the controller. If the node cannot connect to the running
Tor, it starts its own Tor, except on Whonix, where the start fails.

The options `--torControlHost`, `--torControlPort`, `--torControlPassword`, `--torControlCookieFile` and
`--torControlUseSafeCookieAuth`, which configured a running Tor before, stop the start with an explanation which
names `external_tor.config`. This applies on the command line and in a config file. The options must not be ignored,
because the node would then start its own Tor instead of the one the operator configured.

Deleting the outdated Tor files keeps `external_tor.config`, because it is a setting of the user.

### Torrc entries of the user

`--torrcFile` and `--torrcOptions` add entries to the torrc. An entry of `--torrcOptions` overrides an entry of the
file with the same key. Entries for `DataDirectory`, `ControlPort`, `ControlPortWriteToFile`, `HashedControlPassword`,
`SocksPort` and `DisableNetwork` are ignored with a warning, in any case of the name and as the abbreviations which Tor
accepts, because the node needs its own values to control Tor, and because a `SocksPort` entry with flags such as
`NoIsolateSOCKSAuth` would let streams of different peers share circuits. The Tor library applies this rule. An entry
for another key which the node sets, such as `Log`, replaces the value of the node in any case of the name.

### Bridges

As long as the node cannot use bridges and pluggable transports, it must not start Tor if the user configured
bridges. The start fails with an error which explains the risk of connecting without bridges and tells the user to
remove them in the Tor network settings to do so. Bridge lines of 10 characters or fewer, for example the empty line
of an empty custom bridge field, are no bridges, as in netlayer.

## Rationale

Users configure bridges to get around a block of Tor or to hide from their network that they use Tor. Connecting
without the bridges would fail in the first case and expose the user in the second. Failing the start leaves that
decision to the user.

A running Tor is configured in the same file as in Bisq 2, because both use the same Tor library.

## Known gaps

- Bridges and pluggable transports are not supported.
- A running Tor must be reachable on the local host and must accept a cookie or no authentication. A password, the
  SAFECOOKIE method and a remote host are not supported. With the plain cookie method, a process which holds the
  control port while the running Tor does not receives the cookie and the key of the onion service.
- If the running Tor cannot be reached or authentication fails, the Tor library starts its own Tor instead, with only a
  log entry.
