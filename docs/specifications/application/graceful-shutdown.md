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
must request graceful shutdown on the configured UserThread and wait for completion for no longer
than two minutes.

Once a controlled graceful shutdown has completed, its own subsequent `System.exit` must unregister
the common hook before initiating JVM shutdown. Re-entering the already completed graceful shutdown
from that hook provides no additional persistence guarantee and can deadlock the process. Other JVM
shutdown hooks are unaffected.

The hook mechanism must use supported, portable Java APIs. Platform-specific internal signal APIs
must not be required for normal shutdown handling.

## Rationale

The UserThread serializes mutable application state and persistence snapshots. It must remain
available until graceful shutdown and persistence callbacks finish. Separating that work from the
final process exit prevents a wait cycle between the UserThread, `System.exit`, and the JVM shutdown
hook while preserving a bounded best-effort path for external termination.
