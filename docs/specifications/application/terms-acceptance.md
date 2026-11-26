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
- The headless applications, such as the daemon and the bridge, have no window and accept the terms
  without asking.
- The option counts like any other option, also from the configuration file. It is off by default.
  `--acceptTerms=false` or a missing option does not revoke an acceptance which was stored before.
- A new version of the terms comes with a new stored flag, so the user accepts it again, in the
  window or with the option.

## Rationale

A person who starts Bisq without a window, for example in a script or on a server, needs another way
to accept the terms than the window. The option is that way, and it records the same acceptance
as the window, so the user passes it once.
