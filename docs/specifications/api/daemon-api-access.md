# Daemon API access

## Scope

This specification covers who can reach the gRPC API of the Bisq v1 daemon and the password that
the API requires. The desktop application does not start the API.

## Rules

- The daemon does not start without an API password. If `--apiPassword` is empty or blank, it stops
  at startup with a configuration error, before it starts the network or the wallet.
- The API accepts a call only if its `password` header equals the API password.
- The API listens on `127.0.0.1` unless `--apiHost` sets another address. On an address that is not a
  loopback address the daemon logs a warning at startup.
- A failed `unlockwallet`, with a wrong wallet password, does not change the lock state of the
  wallet: a locked wallet stays locked, and an unlocked wallet keeps the key and the timeout of its
  last unlock.

The API can send funds, take offers and set the wallet password, and the wallet is not encrypted by
default. Without a password, every process that can reach the port could do so. The API has no TLS:
on a non-loopback address the password and all calls cross the network in clear text, and every
host that can reach the address can try passwords. A loopback address is reachable only from the
same machine. For remote use, an SSH tunnel to the loopback address keeps the password off the
network.

## Known gaps

- Failed password attempts are not limited, and the password is not compared in constant time.
- Every local process of any user of the machine can reach the loopback address.

## Compatibility

Up to v1.10.11 the daemon started without a password and listened on all interfaces, and a failed
`unlockwallet` left the wallet unlocked with the wrong key. An operator who
started it without a password must now set `--apiPassword`. An operator who calls the API from
another host must now set `--apiHost` or use a tunnel.
