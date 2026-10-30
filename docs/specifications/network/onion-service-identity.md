# Onion service identity

## Scope

This specification covers the key of the onion service under which a Bisq node is reachable over Tor: where it is
kept, when a key may be generated, and how it is protected. Starting and configuring Tor is covered by
[`tor-startup.md`](tor-startup.md).

## Required behaviour

The onion address is the network identity of a node. Offers, trades, disputes and mailbox messages refer to it, so it
must not change unless the user deletes the key. In particular, it must not change when the Tor implementation of
Bisq changes.

The node keeps the key in the `hiddenservice` directory of its Tor directory, in the files which netlayer, the earlier
Tor implementation, wrote:

- `private_key` holds the Base64 encoding of the 64-byte expanded Ed25519 secret key, which Tor's `ADD_ONION` takes,
  between the lines `-----BEGIN OPENSSH PRIVATE KEY-----` and `-----END OPENSSH PRIVATE KEY-----`, without a line
  break at the end. netlayer's jtorctl wrote this armor around the key of Tor's `ADD_ONION` reply and removed it again
  before it passed the key to Tor. Every node which netlayer created has this format.
- `hostname` holds the v3 onion address, including `.onion`.

The node also reads a `private_key` in the format of Tor's control port, `ED25519-V3:` followed by the Base64 encoding.
A key which the node generates is written in netlayer's format, so that an earlier version of Bisq can still use it
after a downgrade.

`private_key` alone defines the identity. The node derives the onion address from it as Tor does: the public key is
the first half of the expanded secret key, read as a little-endian scalar, times the Ed25519 base point (RFC 8032), and
the onion address is derived from the public key according to Tor's rend-spec-v3. `hostname` only shows the address to
the user. If it is missing or holds another address, the node writes the derived address to it and logs that. This
keeps the address when only `private_key` was restored, for example from the rolling backup, which netlayer allowed as
well, because it took the address from Tor.

If `private_key` exists, the node publishes its onion service with that key. It must never generate a new key in that
case. If `private_key` cannot be read, or holds another key type or a key of another length, the start fails with an
error. This includes the RSA key of a version 2 onion service (`-----BEGIN RSA PRIVATE KEY-----`), which Tor does not
support anymore. Generating a new key instead would silently change the identity of the node. If the node cannot determine
whether `private_key` exists, for example without permission to read the directory, the start fails as well.

A key is generated only if `private_key` does not exist: a random 32-byte seed, expanded according to RFC 8032. The
node writes each file to a temporary file in the same directory and moves it in place, so that an interrupted write
does not leave a partial file.

On file systems with POSIX permissions, the `hiddenservice` directory which the node creates is accessible only by the
owner (`0700`), and `private_key` is readable and writable only by the owner (`0600`). The node restores these
permissions of an existing `private_key` at every start, as netlayer did. Symbolic links for `private_key` and
`hostname` are refused, so that the key is not read from or written to another location.

The node keeps a rolling backup of `private_key` in the `hiddenservice` directory. Deleting the outdated Tor files
keeps the `hiddenservice` directory.
