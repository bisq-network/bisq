# Out of memory in a connection

## Scope

This specification covers what the input handler of a connection does when the node runs out of
memory.

## Rules

- An `OutOfMemoryError` in the input handler of a connection is not handled as a fault of the
  connection. It is passed on to the uncaught exception handler of the thread. A headless node, such
  as a seed node, shuts down; the desktop application shows the error.
- Other errors, for example a `StackOverflowError`, close only the connection.

## Rationale

After an `OutOfMemoryError` any thread may have failed in the middle of its work, so the node cannot
rely on its state. Closing only the connection let the node run on in that state.
