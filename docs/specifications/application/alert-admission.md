# Alert admission

## Scope

This specification defines which alert messages a BISQ 1 node accepts from the P2P network, both
when an alert is received and when the alerts received during the bootstrap are processed at
startup.

## Rules

1. A node accepts an alert only if its signature verifies with the alert key the node trusts.
2. An alert that announces a software update or a pre-release carries a version. A node accepts
   such an alert only if the version has the format `major.minor.patch` with decimal digits only,
   for example `1.10.10`. Otherwise the node ignores the alert. An alert without an update or
   pre-release announcement needs no version.
3. The window in which developers create an alert refuses an update or pre-release alert with such
   an invalid version.

## Reasons

- Rule 2: a node compares the version of an update alert with its own version when it shows the
  alert, also at startup. A version in another format cannot be compared. Checking the
  format when the alert is accepted keeps such an alert away from the comparison, so the node never
  has to handle it later.
- Rule 3: the format is also checked where an alert is created, so that no node receives an alert
  with an invalid version.

## Compatibility

No message and no persisted data change. An alert with an invalid version is ignored.

## Not covered

Rules for alerts beyond the signature check and the version format.
