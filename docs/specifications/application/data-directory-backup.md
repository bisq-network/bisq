# Backup of the data directory

## Scope

This specification covers "Backup now" of the desktop application, which copies the data directory of the node into
a directory which the user chooses. The backup is not encrypted: it contains the wallets, the keys of the node and the
key of its onion service ([`../network/onion-service-identity.md`](../network/onion-service-identity.md)).

## Required behaviour

The backup copies the data directory into a new directory named after the time of the backup. Before it copies, the
node writes all data to disk.

The backup copies only regular files and directories. It does not follow symbolic links or junctions of Windows, and it
does not read special files such as named pipes or devices. If the data directory itself is a link, for example to
another drive, the backup follows it. When an entry is not copied, the backup still completes and tells the user
which entries it does not contain, so that the user can copy what they point to.

If a directory or a file cannot be read, the backup fails, instead of completing without it.

On file systems with POSIX permissions, only the owner can access the backup: every directory which the backup
creates gets `0700` and every file `0600`, whatever the permissions in the data directory and the umask are. The files
get these permissions when they are created, so their content is never readable by other users. A file system without
these permissions, such as FAT on a USB drive or the file system of Windows, keeps its own rules.

## Rationale

A link inside the data directory could make the backup copy files from elsewhere into a directory which the user may
keep on a shared drive, and reading a named pipe would block the backup. Whoever can create links in the data
directory can usually read the files of the user anyway, so the rule mainly protects against a writer with less
rights and against mistakes. Telling the user about every entry which is
not copied keeps the backup from silently missing data, for example a wallet directory which the user moved to
another drive and replaced by a link.

Bisq creates most files of the data directory with the default permissions, which let other local users read them
where the home directory lets them in. A backup is often kept on a shared machine or drive, and it holds the key of the
onion service, so another user who reads it could run the node's onion address.
