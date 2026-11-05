# Storage of the key ring

## Scope

This specification covers the files of the key ring of a node: the private keys with which it signs and decrypts its
messages, `sig.key` and `enc.key` in the `keys` directory of its data directory. Every Bisq application has such a key
ring. The key of the onion service is specified in
[`../network/onion-service-identity.md`](../network/onion-service-identity.md).

## Required behaviour

On file systems with POSIX permissions, the `keys` directory is accessible only by its owner (`0700`), and each key
file is readable and writable only by its owner (`0600`). The node gives the directory this mode before it writes a
new key, so that no other user can open the new file before the file has its own mode. It restores both modes of an
existing key ring every time it loads it, so that a key ring of an earlier version gets them at the next start.

The node keeps rolling backups of the key files in `keys/backup`. They are copies with the default mode of the
process, so the mode of the `keys` directory is what keeps them from other users.

If the node cannot set a mode, for example because another user owns the files, it logs a warning and uses the keys
as before. A file system without these permissions, such as the one of Windows, keeps its own rules.

## Rationale

Whoever has the private keys can sign and decrypt the messages of the node, for example the messages of its trades.
The node wrote them with the default mode of the process, `0644` with the usual umask, so every local user who could
enter the data directory could read them; seed nodes, whose data directory was in a home with mode `0755`, were
exposed. The key of the onion service already had these modes. Failing the start when a mode cannot be set would stop
nodes which ran before, so the node warns instead.
