# Access to the data directory

## Scope

This specification covers who can read the data directory of a node (`--appDataDir`). It holds the
wallet files and their rolling backups, the key ring, the key of the onion service, the persisted
data and the logs.

## Rules

- On a file system with POSIX permissions the node creates the data directory, and in it the
  directory of the network, for example `btc_mainnet`, with `keys`, `db`, `tor` and `wallet`, with
  the mode `0700`, so that they are owner-only before anything is written in them, also after a
  start which ends before the log is set up, for example with `--help`, with an option which is not
  supported anymore or, for the daemon, without `--apiPassword`. Parent directories which the node
  creates on the way to the data directory keep the default mode of the process: they are no part
  of the data directory.
- On a file system with POSIX permissions the node sets the mode of the data directory and of every
  directory below it to `0700` at every start, right after the log is set up and before Tor starts,
  so that only their owner can enter them. This includes the directories of an earlier version, and
  a directory which its owner cannot read, such as one with the mode `0300`, together with the
  directories below it.
- The files keep the modes they are created with, by default those of the process. The modes of the
  directories keep them from other users.
- Directories which the node creates while it runs, for example for the backups of the wallets, may
  get the default mode of the process. They lie inside owner-only directories, and the next start
  sets their mode if they belong to the owner of the data directory.
- The node does not follow a symbolic link below the data directory. A directory which such a link
  points to keeps its mode: the user who keeps a part of the data directory elsewhere, for example
  the wallet directory on another drive, protects it there. If `--appDataDir` itself names a link,
  the node sets the modes in the directory which it points to.
- A directory which belongs to another user than the data directory keeps its mode, with everything
  in it, and the node logs a warning. This also holds for a node which runs as root.
- If the node cannot set the mode of a directory, for example because another user owns the data
  directory, it logs a warning, leaves everything below that directory as it is, sets the modes of
  the other directories and starts as before. A file system without these permissions, such as the
  one of Windows, keeps its own rules.
- If the node cannot create the data directory, or one of the directories above, because access is
  denied, for example below a network directory which a start as root created, it does not start
  and says which directory it is. The desktop application shows the error in a dialog.
- The node sets the modes of the directory that `--appDataDir` names and of everything below it, so
  the data directory must be a directory of its own.

The key ring and the backup copy have their own rules:
[`key-ring-storage.md`](key-ring-storage.md) and
[`data-directory-backup.md`](data-directory-backup.md).

## Rationale

The node creates its directories and files with the default mode of the process, `0755` and `0644`
with the usual umask. Only the key ring and the key of the onion service were owner-only. So every
local user who could enter the data directory could read the wallet files, which are not encrypted
without a wallet password, the backups of the wallets, the persisted trades and the logs.

The mode of a directory is checked at each lookup which passes it, and a new mode does not take back
a directory which a process has opened already. Another user who opened a directory inside the data
directory, or made it the working directory, while it was open to others, can open the files which
the node writes there later for as long as that directory itself stays open: the mode of the data
directory does not apply to a lookup which starts below it. So the mode of the data directory alone
does not protect the files below it, and every directory gets the mode, at every start, also the
directories of an earlier version, which other users could open at any time before. A file is
opened by a lookup in its directory, so the files need no mode of their own.

A data directory can hold directories of several users, for example after a start as root. The owner
of a directory, and everyone who may write in it, can replace a directory in it with a link at any
time, so the node could set the mode of a directory outside the data directory if it entered such a
directory. And a node which runs as root could set the mode of a directory of root in which the user
of the data directory works, which would lock that user out. So the node enters only directories of
the owner of the data directory. It sets the mode of a directory before it reads the entries, so that
only the owner can replace them, and enters a directory only if it could set its mode.

A start which ends before the log is set up sets no mode. If it created the data directory with the
default mode, other users could open its directories until a later start gets that far, and keep
them open. Created owner-only, a new data directory is never open to others.

## Known gaps

The directories of another user in a data directory stay as open as their owner made them. They must
get the owner of the data directory, for example with `chown -R` as root.

A node which runs as root does not protect itself from the owner of the data directory, if that is
another user, or from a user who controls the path of the data directory. Such a user can replace a
directory with a link on the way, and can do the same with the files which the node writes.

A mode does not take back a file which another user opened, or linked with a hard link where the
system allows it, while the data directory was open to others, and no mode takes back a copy. A file
which the node writes in place, such as the log until it rolls over, stays readable that way. The
node writes the wallet files, their backups and the persisted data as new files, so such a user keeps
only their content of that time. On a host where other users could enter the data directory of an
earlier version, they could have copied the wallet: if it has no password, move the funds to a new
wallet.

## Compatibility

A data directory of an earlier version gets the modes at the next start. An operator who reads files
of the data directory as another user, for example the logs or the JSON files of a full DAO node,
must do so as the owner now, because the node sets the modes again at every start. On Linux a new
mode also limits what ACL entries grant other users on a directory, as it sets the ACL mask, so
access through ACLs does not survive a start either.
