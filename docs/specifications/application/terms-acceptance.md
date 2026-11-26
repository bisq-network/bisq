# Acceptance of the terms and conditions

## Scope

This specification covers when a node asks its user to accept the terms and conditions, and how the
user accepts them with the `--acceptTerms` option instead of the terms window.

## Rules

- At start, before the P2P network, Tor and the wallet start, the setup checks whether the terms of
  the current version were accepted. If they were, the setup continues at once.
- If they were not and the start has `--acceptTerms=true`, the user accepted them with that option.
  The setup stores the acceptance like an acceptance in the window, logs that the option accepted
  them and continues.
- Otherwise the desktop application shows the terms window. The setup continues only after the user
  accepted them there, and stores the acceptance. A user who declines ends the application.
- The headless applications, such as the daemon and the bridge, have no window. Without an earlier
  acceptance and without the option they log an error with the text of the terms and the option
  which accepts them, and shut down before the P2P network, Tor and the wallet start. Releases before
  this rule accepted the terms in these applications without asking.
- The option counts like any other option, also from the configuration file. It is off by default.
  `--acceptTerms=false` or a missing option does not revoke an acceptance which was stored before.
- A new version of the terms comes with a new stored flag, so the user accepts it again, in the
  window or with the option.

## Rationale

A person who starts Bisq without a window, for example in a script or on a server, needs another way
to accept the terms than the window. The option is that way, and it records the same acceptance
as the window, so the user passes it once. A headless application which accepted the terms by
itself would accept them for a user who never saw them, so it prints them and waits for the option.

## Known gaps

- `accept-terms-future-versions-unseen`: an option which stays in a start script or in the
  configuration file also accepts later versions of the terms, which come with a new stored flag,
  without the user seeing them. Whether the option should name the version of the terms it accepts
  is open.

- The shutdown of a headless application without an acceptance ends with the exit status 0, like
  the other shutdowns of these applications at start. A supervisor which restarts only failed
  processes does not restart it, which is the wanted result, but it cannot tell this shutdown from
  a normal one.

## Compatibility

Earlier releases accepted the current terms in a headless application by themselves and stored
that acceptance, so a daemon or bridge with such a data directory starts as before. A daemon or
bridge with a new data directory, or one which accepted only older terms, must start once with
`--acceptTerms=true`. Releases before this option reject it as an unknown command line option.
