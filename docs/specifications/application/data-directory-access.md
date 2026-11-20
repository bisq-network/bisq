# Access to the data directory

## Scope

This specification covers who can read the data directory of a node (`--appDataDir`). It holds the
wallet files and their rolling backups, the key ring, the key of the onion service, the persisted
data and the logs.

## Rules

- On a file system with POSIX permissions the node sets the mode of the data directory to `0700` at
  every start, right after the log is set up, so that only its owner can enter it.
- The files and directories inside keep the modes they are created with, by default those of the
  process. The mode of the data directory keeps them from other users.
- If the node cannot set the mode, for example because another user owns the directory, it logs a
  warning and starts as before. A file system without these permissions, such as the one of Windows,
  keeps its own rules.
- The node sets the mode of the directory that `--appDataDir` names, so the data directory must be a
  directory of its own.

The key ring and the backup copy have their own rules:
[`key-ring-storage.md`](key-ring-storage.md) and
[`data-directory-backup.md`](data-directory-backup.md).

## Rationale

The node creates its directories and files with the default mode of the process, `0755` and `0644`
with the usual umask. Only the key ring and the key of the onion service were owner-only. So every
local user who could enter the data directory could read the wallet files, which are not encrypted
without a wallet password, the backups of the wallets, the persisted trades and the logs. One mode at
the top protects all of them, also the files of an earlier version.

## Compatibility

A data directory of an earlier version gets the mode at the next start. An operator who reads files
of the data directory as another user, for example the logs, must do so as the owner now, because
the node sets the mode again at every start.
