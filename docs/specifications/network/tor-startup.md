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

The node logs the version which the running Tor reports. The commands, replies and events of the control connection
are logged at debug level only, because they contain onion addresses.

### Running Tor

The node uses a running Tor instead of its own if `external_tor.config` in its Tor directory sets `UseExternalTor 1`,
if the environment variable `TOR_SKIP_LAUNCH` is `1`, `true` or `yes`, or if it runs on Whonix. `external_tor.config`
sets the host and port of the control port and whether a cookie authenticates the controller. The node uses the SOCKS
port which the running Tor reports, on the host of the control port.

With a cookie, the node authenticates with the SAFECOOKIE method: Tor first proves that it knows the cookie, and the
node never sends the cookie. A process which listens on the control port instead of Tor gets neither the cookie nor the
key of the onion service, and the start fails. A cookie file which does not hold the 32 bytes of a Tor cookie fails the
start. `ControlPassword` in `external_tor.config` sets the password of a running Tor which uses `HashedControlPassword`
instead of a cookie; setting it together with `CookieAuthentication 1` fails the start. With a password, the process on
the control port cannot prove that it is Tor, so the cookie is the better choice on the same system. Without a cookie
and a password, the node sends an empty authentication, which only fits a control port that other processes cannot
reach, such as the filtered control port of Whonix.

On Whonix, the node always uses the Tor of the Whonix-Gateway. onion-grater filters the control port of the Gateway and
needs the profile of Bisq there (`sudo onion-grater-add 40_bisq`). onion-grater accepts only the empty authentication,
so the node uses it there also with `CookieAuthentication 1`; the cookie of a Whonix-Workstation is a dummy. The node
listens on all addresses, because the Tor of the Gateway connects to the address of the Workstation. The publication of
the onion service completes at the start or at the end of the upload of its descriptor, because older profiles of
onion-grater deliver only the end in a form which the node accepts.

Before it uses the running Tor, the node waits until that Tor has bootstrapped, at most 4 minutes, as netlayer did. It
does not change the configuration of the running Tor. The restarts with fresh files, which the node does for its own
Tor, do not apply to a running Tor: if it does not bootstrap in time, the start fails at once.

If the node cannot use the running Tor, the start fails with an error which names the host and port and the cause. The
node never starts its own Tor instead. The user may rely on the running Tor, for example for its bridges, and the own
Tor would connect directly to the Tor network.

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

The user chooses in the Tor network settings: no bridges, the provided bridges of a type (obfs4, snowflake or meek), or
custom bridge lines. The provided bridges are the built-in bridges of the Tor Browser version of the bundled Tor, so
they change with the Tor version. The node reads the choice at every start. It does not use the bridge lines which an
earlier version stored for the provided bridges, because they become outdated. The settings store the type as a number.
Earlier versions stored obfs4, obfs3, meek-amazon or meek-azure. obfs3 is read as snowflake and both meek types as
meek, because obfs3 has no bridges anymore and the old meek fronts stopped working. The settings still store the bridge
lines of the choice, because versions before the Tor library read only these lines.

With bridges, the Tor of the node connects only through them. The programs of the pluggable transports come with the
Tor bundle: the node installs them on macOS, Windows and Linux x86_64. On Linux ARM64 they must be in `PATH`. If the
node cannot use the chosen bridges, for example because a transport has no program, the start fails. The node never
connects without the chosen bridges. Custom bridge lines of 10 characters or fewer, for example the empty line of an
empty custom bridge field, are no bridges, as in netlayer. If no line remains, the node connects without bridges.

A running Tor uses the bridges of its own configuration. The node then ignores the bridges of its settings and logs a
warning.

If Tor cannot connect, or the chosen bridges cannot be used, the start fails, and the desktop application opens the
Tor network settings so that the user can choose bridges, as with netlayer.

The node does not log bridge lines, because a bridge which a user got privately is blocked once it is known.

## Rationale

Users configure bridges to get around a block of Tor or to hide from their network that they use Tor. Connecting
without the bridges would fail in the first case and expose the user in the second. Failing the start leaves that
decision to the user.

A running Tor is configured in the same file as in Bisq 2, because both use the same Tor library.

## Known gaps

- On Linux ARM64 the Tor bundle has no programs for the pluggable transports, so the user has to install them.
- A running Tor on another host reaches the onion service of the node only at `127.0.0.1` of its own host, for example
  in a shared network namespace or on Whonix, where onion-grater rewrites the target.
