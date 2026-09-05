# Graceful application shutdown

## Scope

This specification covers process termination for Bisq executables that use the common application
setup. It distinguishes an application-initiated controlled exit from JVM termination initiated by
the operating system or by code outside the graceful-shutdown flow.

## Required behaviour

### Controlled exit

An application-initiated shutdown must complete its applicable service shutdown and final persistence
flush before scheduling process termination. The supplied completion handler must be notified before
termination, including when shutdown happens before dependency injection is complete or when a
downgrade deliberately suppresses persistence.

Service shutdown operations that can wait without a bound for network, socket, subprocess, or
control-protocol I/O must not run on the UserThread. Their completion may be bounded by a timeout,
and the UserThread must remain available to execute that timeout and the final persistence flush if
the external operation does not return.

Executors needed by delayed shutdown work must remain available until that work completes or is
cancelled. In particular, messages deliberately broadcast during shutdown must be submitted before
their executor stops accepting work.

Once network shutdown has started, connection lifecycle events no longer describe runtime state and
must not be published to observers. Disconnects caused by the shutdown itself must not be counted as
peer connection faults, must not change persisted peer data, and must not schedule further work. This
applies to the controlled exit and to the JVM shutdown-hook backstop alike, so that the observable
outcome does not depend on how termination was triggered.

A shutdown request received while graceful shutdown is already in progress must join the in-progress
shutdown. Its completion handler must be notified when that shutdown completes; the repeated request
must neither start shutdown work again nor report completion before the work has completed. This
single-entry rule includes executable-specific service shutdown performed by subclasses.

Process termination must not run on the UserThread. `System.exit` waits for JVM shutdown hooks, and a
hook may itself require the UserThread; initiating it there can create a circular wait. The process
must retain the requested exit status, including the failure status used to trigger wrapper-script
restarts for seed, statistics, bridge, and REST nodes.

Only the first controlled exit request may schedule process termination. Competing normal, timeout,
and error paths must not create multiple exit threads or replace the status chosen by the first
completed path. A later failure-triggered request joining an already running normal shutdown must not
mutate the exit status captured by that normal shutdown.

### JVM shutdown-hook backstop

The common JVM shutdown hook is a backstop for termination initiated outside the controlled flow. It
must first move UserThread dispatch to a serial executor and timer implementation that remain
available throughout shutdown, then request graceful shutdown there and wait for completion for no
longer than two minutes. An executor configured specifically for that purpose must not be replaced by
later configuration of the regular executor, because the resulting defect is only observable when the
process is terminated externally. For the GUI this shutdown path must not depend on the JavaFX
application thread or JavaFX timers: JavaFX can dispose its toolkit concurrently from its own JVM
shutdown hook, after which queued JavaFX work is not guaranteed to run. Existing JavaFX timers
must not execute their callbacks or require explicit toolkit access once external JVM shutdown has
begun.

The transition to the shutdown executor ends presentation-event delivery. Presentation-layer
subscribers must ignore domain notifications emitted by shutdown work instead of mutating
JavaFX-observable state, while the underlying domain operation and any required network broadcast
must continue. Uncaught-error handling during external shutdown must remain logging-only rather than
attempting to display UI. The transition itself is not synchronised with work already queued on the
replaced executor. That work may still run concurrently and is not awaited, so the shutdown path must
not depend on it having completed. Library callbacks that were registered with the UserThread executor
before the transition must resolve the current executor at dispatch time, so events raised by shutdown
work are serialised with it instead of being delivered to the replaced executor.

Resources governed by graceful shutdown must have one shutdown owner. A library or component must
not independently tear down the same resource from a concurrent JVM hook when doing so can violate
the application shutdown order. Abrupt process termination may still rely on operating-system or
protocol ownership semantics as a final backstop.

Once a controlled graceful shutdown has completed, its own subsequent `System.exit` must unregister
the common hook before initiating JVM shutdown. Re-entering the already completed graceful shutdown
from that hook provides no additional persistence guarantee and can deadlock the process. Other JVM
shutdown hooks are unaffected.

The hook mechanism must use supported, portable Java APIs. Platform-specific internal signal APIs
must not be required for normal shutdown handling.

## Rationale

The UserThread serializes mutable application state and persistence snapshots. It must remain
available until graceful shutdown and persistence callbacks finish. A GUI-specific shutdown
executor preserves that serialization without depending on the concurrently terminating JavaFX
toolkit. Moving potentially unbounded external teardown off that executor keeps the timeout and
persistence paths responsive. Separating that work from the final process exit prevents a wait cycle
between the UserThread, `System.exit`, and the JVM shutdown hook while preserving a bounded
best-effort path for external termination.
