# Tor startup and configuration

## Scope

This specification covers how a Bisq node starts Tor or connects to a running Tor, which user settings apply, and how
the start fails. The key of the onion service is covered by
[`onion-service-identity.md`](onion-service-identity.md).

## Required behaviour

### Tor of the node

On every operating system, the node runs the Tor of the Tor expert bundle which the Bisq build downloaded and verified:
its PGP signature, the SHA-256 hash which the build pins for the platform, and the version of its tor. The node installs
the bundle into `bin/` of the Tor directory and never runs a `tor` of the system; the Linux packages of Bisq do not
depend on the `tor` package. Linux x86_64, macOS and Windows x86_64 get the bundle of a stable Tor Browser release.
Linux ARM64 gets the bundle of an alpha release with the same tor version, because no stable release has a bundle for
Linux ARM64. On Linux, the bundled Tor needs glibc 2.17 or newer (2.27 on ARM64) and the zlib library of the system
(`libz.so.1`).

The node owns the Tor it starts. Tor gets the process ID of the node as its owning controller and stops when the node
stops. If the start fails after Tor was launched, including the publication of the onion service, the node stops that
Tor, so that no node remains which reaches peers but cannot be reached. A shutdown during the start also stops the
Tor which the start launches. When the start of Tor or of the onion service fails, the seed, statistics, bridge and
REST nodes shut down with the failure exit status, so that systemd or their start script starts them again, instead of
staying unreachable until their periodic restart. At the shutdown, the node waits for the stop of its Tor: the Tor
library asks Tor to exit and kills it after 3 seconds, which takes at most about 6 seconds, and the node waits at most
8 seconds for the close of its connections and the stop of Tor.

The node starts its Tor, or sandy with Tor, in a session of its own with the `setsid` program of the system, if the
system has one, as Linux does. A Ctrl+C in the terminal which started the node then reaches only the node, whose
shutdown still uses Tor, for example to remove the offers of the user, and stops Tor at its end. macOS has no `setsid`:
there Tor stays in the process group of the node, and a Ctrl+C in the terminal ends Tor at once. For the same reason,
the systemd unit of the seed node sends the stop signal only to the node; systemd kills the processes which are left
after the node exited, or after the stop timeout.

If Tor does not bootstrap within 4 minutes, the node stops it, deletes the files in the Tor directory except the
`hiddenservice` directory and `external_tor.config`, and starts Tor again, up to 5 starts. Tor sometimes cannot
bootstrap with the files of an earlier run; netlayer retried the same way.

Before a start, the node leaves the lock file of the data directory of Tor to Tor, which locks the file itself: a file
which an ended Tor left does not block the start, and a Tor which still runs on the data directory makes the new Tor
exit instead of sharing the directory with it.

The publication of the onion service completes once an HSDir has stored its descriptor (the event `HS_DESC UPLOADED`),
not when Tor starts the upload, so that peers can find the onion service when the node reports it as published. The
node waits for it without a time limit, as netlayer did, because Tor keeps trying to upload the descriptor.

Tor must not read a configuration file outside the Tor directory of the node. The node therefore also passes the torrc
which it generates as the defaults torrc, instead of the built-in default location. The bundled Tor has the paths of
its build below `/var/tmp/dist` compiled in, which other local users can create. The node therefore sets the GeoIP
files of the bundle in the torrc, and points `OPENSSL_CONF` to an empty file in the Tor directory, because OpenSSL would
otherwise read its configuration there and could load modules from it.

The node logs the version which the running Tor reports. The commands, replies and events of the control connection
are logged at debug level only, because they contain onion addresses. Tor writes its own log to its standard output,
which the node writes to `tor-stdout.log` in the Tor directory; above 10 MiB it moves the log to `tor-stdout.log.1`
before the next start. The standard error of Tor, where sandy also reports why it did not start Tor, goes to
`tor-stderr.log`. "Zip log files" of the desktop application adds these three files, if they exist, to the logs of
the node, because warnings of Tor, for example about the clock or the bridges, are only there.

The Tor directory has the parts `bin/` (the installed bundle), `data/` (the data directory of Tor) and `sandy/` (the
sandbox program and its status record, see "Sandbox"). The torrc, the logs, `external_tor.config`, the record
`data-mode` (see "Sandbox") and the `hiddenservice` directory with the key of the onion service lie in the Tor
directory itself, outside the directory which Tor writes. Before a start of its own Tor, the node moves the state of
Tor of the layout before into `data/`, deletes only the files of the old layout which it knows, and keeps everything
else. The `hiddenservice` directory, its key backups and files of the user stay where they are, also after a reset
which deleted `data/`. The node chooses the ports of the control and SOCKS listeners itself and checks with the hash
of the control password, and after the bootstrap with the SOCKS listener, that it talks to the Tor of this start.

### Running Tor

The node uses a running Tor instead of its own if `external_tor.config` in its Tor directory sets `UseExternalTor 1`,
if the environment variable `TOR_SKIP_LAUNCH` is `1`, `true` or `yes`, or if it runs on a Whonix-Workstation.
`external_tor.config` sets the host and port of the control port and whether a cookie authenticates the controller.

The node uses the first SOCKS listener on TCP which the running Tor reports and which fits: a listener on a specific
address at that address, a listener on a wildcard address (`0.0.0.0` or `::`) at the host of the control port. A
loopback listener names the host of Tor, so the node uses it only if the control host is a loopback address as well;
otherwise it would connect to whatever listens on that port of its own host. If no listener fits, the start fails with
an error which names the listeners and asks for a `SocksPort` on an address which this host can reach.

The node uses a running Tor only if the SOCKS port which it uses isolates streams by their SOCKS credentials
(`IsolateSOCKSAuth`, the default of Tor), because it keeps its connections apart with them
([`tor-stream-isolation.md`](tor-stream-isolation.md)). If an entry of `SocksPort` which may have opened that port
turns the isolation off with `NoIsolateSOCKSAuth`, the start fails with an error which names the entry and says to
remove the flag. If the control port does not answer the query of the configuration, as onion-grater filters it on
Whonix, the node logs that it could not check the port and goes on.

The node connects to the control port of a running Tor again only while the connection is refused, up to ten attempts
200 ms apart, because the Tor which should listen there may not be up yet. If the connection does not complete within
10 seconds, or the host cannot be reached, the start fails at once with an error which says so.

With a cookie, the node authenticates with the SAFECOOKIE method: Tor first proves that it knows the cookie, and the
node never sends the cookie. A process which listens on the control port instead of Tor gets neither the cookie nor the
key of the onion service, and the start fails. A cookie file which does not hold the 32 bytes of a Tor cookie fails the
start. `ControlPassword` in `external_tor.config` sets the password of a running Tor which uses `HashedControlPassword`
instead of a cookie; setting it together with `CookieAuthentication 1` fails the start. With a password, the process on
the control port cannot prove that it is Tor, so the cookie is the better choice on the same system. Without a cookie
and a password, the node sends an empty authentication, which only fits a control port that other processes cannot
reach, such as the filtered control port of Whonix.

On a Whonix-Workstation, the node always uses the Tor of the Whonix-Gateway. onion-grater filters the control port of
the Gateway and needs the profile of Bisq there (`sudo onion-grater-add 40_bisq`). onion-grater accepts only the empty
authentication, so the node uses it there also with `CookieAuthentication 1`; the cookie of a Whonix-Workstation is a
dummy. The node listens on all addresses, because the Tor of the Gateway connects to the address of the Workstation.
The publication of the onion service completes once an HSDir has stored the descriptor, as elsewhere; every profile of
onion-grater delivers that event. Profiles from before September 2024 also shorten the event of the start of the
upload, without its HSDir index; such a shortened start completes the publication, as before.

On a Whonix-Gateway, whose Tor serves the Workstations behind it, the node does not run: the start of Tor fails before
anything else with an error which says to run Bisq on a Whonix-Workstation. Both systems have the markers of Whonix,
`/usr/share/whonix/marker` and `/usr/share/anon-dist/marker`; a Gateway also has
`/usr/share/anon-gw-base-files/gateway`. The node and the Tor library decide with one rule, the one of the library.

Before it uses the running Tor, the node waits until that Tor has bootstrapped, at most 4 minutes, as netlayer did. It
does not change the configuration of the running Tor. The restarts with fresh files, which the node does for its own
Tor, do not apply to a running Tor: if it does not bootstrap in time, the start fails at once.

If the node cannot use the running Tor, the start fails with an error which names the host and port and the cause. The
node never starts its own Tor instead. The user may rely on the running Tor, for example for its bridges, and the own
Tor would connect directly to the Tor network.

The options `--torControlHost`, `--torControlPort`, `--torControlPassword`, `--torControlCookieFile` and
`--torControlUseSafeCookieAuth`, which configured a running Tor before, stop the start with an explanation which
names `external_tor.config` and the config file. This applies on the command line and in a config file. The desktop
application shows the explanation in a dialog, because it is usually started without a terminal. The options must not be ignored,
because the node would then start its own Tor instead of the one the operator configured. On a Whonix-Workstation, the
node ignores them with a warning, because there it always uses the Tor of the Whonix-Gateway and never starts its own
Tor. The Whonix guide for Bisq passes `--torControlPort=9051 --torControlPassword=notrequired`. On a Whonix-Gateway the
node ignores them too, so that the start reaches the refusal which names the Workstation instead of an error which
points to `external_tor.config`.

Deleting the outdated Tor files keeps `external_tor.config`, because it is a setting of the user.

### Torrc entries of the user

`--torrcFile` and `--torrcOptions` add entries to the torrc. An entry of `--torrcOptions` overrides an entry of the
file with the same key. Entries for `DataDirectory`, `ControlPort`, `ControlPortWriteToFile`, `HashedControlPassword`,
`SocksPort` and `DisableNetwork` are ignored with a warning, in any case of the name, as the abbreviations which Tor
accepts, and in the hidden forms which Tor accepts for the listeners, such as `__SocksPort` and `__ControlPort`,
because the node needs its own values to control Tor, and because a `SocksPort` entry with flags such as
`NoIsolateSOCKSAuth` would let streams of different peers share circuits; a hidden form would add a second listener.
Entries which would give another program control over Tor are ignored with a warning in the same way, because the node
alone controls its Tor: `ControlSocket`, `ControlSocketsGroupWritable`, `CookieAuthentication`, `CookieAuthFile`,
`CookieAuthFileGroupReadable`, `__HashedControlSessionPassword` and `__OwningControllerFD`. The Tor library applies
this rule. An entry for another key which the node sets, such as `Log`, replaces the value of the node in any case of
the name and as an abbreviation which Tor accepts, for example `AvoidDiskWrite` for `AvoidDiskWrites`. An entry for a
longer option which only starts like such a key, such as `LogTimeGranularity`, replaces nothing.

An entry which makes Tor start a program, `ClientTransportPlugin` or `ServerTransportPlugin` in any case of the name
and as an abbreviation which Tor accepts, is accepted only if the command line sets the option which brings it. If
such an entry comes from `--torrcOptions` in a config file, or from a torrc file which a config file names, the start
fails with an explanation which names the entry. The node writes `bisq.properties` itself, and whoever can change a
config file in the data directory could otherwise run a program with the rights of the user at the next start of
Tor, also with the sandbox off.

### Bridges

The user chooses in the Tor network settings: no bridges, the provided bridges of a type (obfs4, snowflake or meek), or
custom bridge lines. The provided bridges are the built-in bridges of the Tor Browser version of the bundled Tor, so
they change with the Tor version. The node reads the choice at every start. It does not use the bridge lines which an
earlier version stored for the provided bridges, because they become outdated. The settings store the type as a number.
Earlier versions stored obfs4, obfs3, meek-amazon or meek-azure. obfs3 is read as snowflake and both meek types as
meek, because obfs3 has no bridges anymore and the old meek fronts stopped working. The settings still store the bridge
lines of the choice, because versions before the Tor library read only these lines.

With bridges, the Tor of the node connects only through them. The programs of the pluggable transports come with the
Tor bundle, which the node installs on every operating system; programs on the `PATH` are not used. If the node cannot
use the chosen bridges, for example because a transport has no program, the start fails. The node never connects
without the chosen bridges. Custom bridge lines of 10 characters or fewer, for example the empty line of an empty
custom bridge field, are no bridges, as in netlayer. If no line remains, the node connects without bridges.

A running Tor uses the bridges of its own configuration. The node then ignores the bridges of its settings and logs a
warning.

If Tor cannot connect, or the chosen bridges cannot be used, the start fails, and the desktop application opens the
Tor network settings so that the user can choose bridges, as with netlayer. The settings then show the error at their
top, and no popup shows it as well, because it would lie behind them.

The node does not log bridge lines, because a bridge which a user got privately is blocked once it is known.

### Sandbox

The node runs the Tor which it starts in the sandbox of the operating system with sandy, unless it is started with
`--useTorSandbox=false`. This applies to every Bisq application which starts Tor: the desktop application, the daemon,
the seed, statistics, bridge and REST nodes and the BTC node monitor. The inventory monitor takes positional arguments;
only `0` as its seventh argument turns the sandbox off, and its errors name that argument instead of the option. Every
build contains the sandy binary of its platform, which the Tor build checks against its pinned SHA-256 hash.

The base level of the sandbox is the maximum lockdown. Tor may read its torrc and the bundle and write only its data
directory; on Linux and macOS every other path looks missing to it. Tor has a network of its own: the node reaches the
control and SOCKS listeners through ports which sandy relays, Tor reaches the onion service of the node only through a
port which sandy forwards, and Tor connects out only through the egress broker of sandy, which reaches public
addresses only. Tor cannot start other processes, cannot signal processes outside the sandbox, and ends when the node
ends. Where the system cannot enforce the base level, sandy selects a weaker level of the policy: below Linux 6.12 a
PID namespace isolates the signals of Tor, and where unprivileged user namespaces are restricted or off, Tor gets the
network of the system. Only when the Tor of a start answers on its control port does the node report the level and
log that Tor runs in it, with a warning for a level with the network of the system or without isolated signals. Until
then the start reports no sandboxed Tor, also after sandy selected a level, so that a start which fails before Tor
answers never claims a sandboxed Tor.

For a level with the network of the system or without isolated signals, the desktop application also shows a notice
once per start, after its splash screen. It names the level, what the level gives up, and when Tor gets the stronger
protection: the network of its own without bridges whose pluggable transports need the network of the system, and on
Linux only where the sandbox may use unprivileged user namespaces with a kernel that has the fix of Landlock erratum 3;
the isolated signals on Linux 6.12 and newer, or where the sandbox may use unprivileged user namespaces. "Don't show
again" hides the notice only for the same combination of weaknesses, so that a new weakness shows it again. A sandbox
which the user turned off and a running Tor get no notice: the user chose them, and the log names them. The other
applications only log the status.

Pluggable transports are programs which Tor starts for bridges. The sandbox lets Tor start them only with
`--allowPluggableTransportsInTorSandbox=true`; the option is off by default. The Tor network settings have a switch for
it, which asks for a confirmation before it allows them, writes the option into `bisq.properties` of the data
directory (and removes it when turned off), and takes effect after a restart; the switch is disabled while the sandbox
is off, on Windows, and while the option is given on the command line, which overrides `bisq.properties`; a note then
names the value of the command line. With the option, Tor may start the programs of the transports of the chosen bridges, and they
end with Tor. On Linux, obfs4 keeps the network of the sandbox and connects through the egress broker; with meek,
webtunnel, snowflake or conjure, Tor and its transports get the network of the system. On macOS every transport gets
the network of the system. Only when the bridges use a transport which verifies its server through the system,
webtunnel, snowflake or conjure, does the sandbox let Tor and its transports reach the certificate verifier of the
system (the Mach service `com.apple.trustd.agent`); with obfs4 or meek bridges alone it does not. On Windows Tor cannot
start pluggable transports in its sandbox.

If the sandbox is on, the start fails with an error which names the reason and the option to turn the sandbox off, and
the node never starts Tor without the sandbox instead, when:

- the bridges need pluggable transports and `--allowPluggableTransportsInTorSandbox` is not `true`, or the system is
  Windows;
- a torrc entry of the user sets a proxy option or `ServerTransportPlugin`, or without bridges sets
  `ClientTransportPlugin`, also as an abbreviation of the option name, which Tor accepts;
- a torrc entry of the user sets `GeoIPFile` or `GeoIPv6File`, also as an abbreviation: the sandbox lets Tor read only
  the GeoIP files of the bundle, and with another file a country selector such as `ExcludeNodes {us}` would match no
  relay, without an error;
- in a level in which Tor connects out only through the egress broker, which reaches public addresses only, every
  bridge is on an address of this host or of the local network. If only some bridges are, the node logs a warning with
  their number; neither names the addresses;
- the system can enforce no level of the sandbox, for example Linux without Landlock, or the build contains no sandy;
- sandy cannot tell now whether the system enforces the sandbox;
- sandy refused to run Tor. The node knows that from the status record of sandy, `sandy/run-status` in the Tor
  directory, which Tor can neither write nor remove. A sandboxed Tor which ends before it answers, also with the
  status of a refusal, gets an error without the advice to turn the sandbox off, so that a compromised Tor cannot make
  the node give that advice, after which the next Tor would run without the sandbox.

The node reserves the ports of the sandbox by binding and releasing them before sandy binds them. When sandy refuses
with status 125, in its check or in its run before Tor ran, as it does when another process took one of these ports in
between, the node starts once more with new ports; a second refusal fails the start with the advice. Other failures
are not retried.

The error of a failed sandboxed start names first what the node knows itself, the status record, the exit status and
on Windows the hint about the session, and quotes the standard error of sandy and Tor last, labeled as written by sandy
and by Tor in its sandbox and not verified by the node, because a sandboxed Tor can write that file.

The data directory of Tor, `data/`, is the only path which a sandboxed Tor may write. The node records in `data-mode` of
the Tor directory, outside the paths which Tor may write, whether the Tor of a start ran in the sandbox. Before Tor
runs without the sandbox after it ran in it, the node clears `data/`, so that a Tor with all rights of the user does
not parse what a compromised sandboxed Tor may have written there. Tor then chooses new entry guards and downloads the
directory again. A start in the sandbox keeps `data/`, because the sandboxed Tor is confined whatever it parses. A
`data/` without a record counts as written in the sandbox.

When the bridges need pluggable transports which the sandbox does not allow, the error names the transports and,
instead of the option, the switch of the Tor network settings which allows them, or on Windows the option to turn the
sandbox off, because the switch is disabled there, and the choice of no bridges or of bridges without pluggable
transports. The desktop application then opens the Tor network settings with the error, as for bridges which cannot be
used, so that the user can allow the transports or choose other bridges.

A running Tor, including the Tor of Whonix, runs without the sandbox of the node. The sandbox never touches
`hiddenservice`: the node sends the key of the onion service to Tor over the control connection.

## Rationale

Users configure bridges to get around a block of Tor or to hide from their network that they use Tor. Connecting
without the bridges would fail in the first case and expose the user in the second. Failing the start leaves that
decision to the user.

A running Tor is configured in the same file as in Bisq 2, because both use the same Tor library.

The node runs the Tor of the bundle on Linux as well, so that every platform runs the tor version and the pluggable
transports which the build verified, independent of the version and the patch level which a distribution packages.

Tor parses data from the network, and the sandbox keeps a compromised Tor away from the wallet and the keys of the
node. A start which cannot get the sandbox fails instead of running Tor without it, so that the user does not lose the
sandbox without being told. Pluggable transports stay off by default, because with them Tor may start processes and,
with most transports, reaches the network of the system, including its local services.

## Known gaps

- On Linux ARM64 the node runs the Tor of an alpha release of Tor Browser, because no stable release has a Tor expert
  bundle for Linux ARM64. The build pins that bundle by its hash and requires the tor version of the stable bundle.
- Bridges with pluggable transports need the sandbox off on Windows.
- On Windows Tor runs in its sandbox only in a desktop session. In session 0, where services and logins over SSH run,
  Tor ends at once when it starts in its sandbox; the report of sandy in the log of the node says so.
- At a level with the network of the system, and with pluggable transports other than obfs4 on Linux, the sandbox
  does not keep Tor from connecting to local services of the user, for example a local Bitcoin node or the API of the
  daemon; their own authentication protects them. Tor cannot read the wallet and the keys of the node.
- A running Tor on another host reaches the onion service of the node only at `127.0.0.1` of its own host, for example
  in a shared network namespace or on Whonix, where onion-grater rewrites the target.
