# Running a seed node as a daemon

This document presents some steps to be able to run a Bitsquare seed node as
an unattended daemon in a GNU/Linux server with a traditional System V init.

## Before you start

We assume that you have already configured a Bitsquare seed node to run in a
computer.  You will need to upload the seed node code and configuration to the
server:

  - The code is contained in the ``SeedNode.jar`` file which is usually left
    under ``seednode/target`` after building Bitsquare.
  - The seed node configuration is the ``Bitsquare_seed_node_HOST_PORT``
    directory under ``~/.local/share`` (Unix), ``%APPDATA%`` (Windows) or
    ``~/Library/Application Support`` (Mac OS X).

## Dedicated user

In order to avoid security risks, it is highly recommended that you create a
dedicated user in the server to run the seed node daemon, and that you forbid
other users to access its files, for instance:

    # adduser bsqsn
    # chmod go-rwx ~bsqsn

Place the jar file where the ``bsqsn`` user can read it and tag it with
Bitsquare's version number (to allow running several instances of mutually
incompatible versions), e.g. ``~bsqsn/SeedNode-VERSION.jar``.  Copy the
configuration directory to the ``~bsqsb/.local/share``  directory.

## Testing the seed node

You need to check that the seed node can actually run in your system.  For
instance, if you are using version 0.4.2 and your seed node's Tor address is
``1a2b3c4d5e6f7g8h.onion:8000``, try to run this as the ``bsqsn`` user:

    $ java -jar ~bsqsn/SeedNode-0.4.2.jar 1a2b3c4d5e6f7g8h.onion:8000 0 50

Please check error messages if it fails to run.  Do note that you will need
OpenJDK and OpenJFX in the server.  In Debian-like systems you may install the
needed dependencies with:

    # apt-get --no-install-recommends install openjfx

After the node runs successfully, interrupt it with Control-C.

## Init script

To allow the daemon to start automatically on system boot, use the attached
[init script](bitsquare-sn.init.sh).  First edit it and change its
configuration variables to your needs, especially ``SN_ADDRESS``, ``SN_JAR``
and ``SN_USER``.  In the previous example, the values would be:

    SN_ADDRESS=1a2b3c4d5e6f7g8h.onion:8000
    SN_JAR=~bsqsn/SeedNode-0.4.2.jar
    SN_USER=bsqsn

Put the customized script under ``/etc/init.d`` using a name without
extensions (e.g. ``bitsquare-sn``), make it executable, add it to the boot
sequence and finally start it:

    # cp /path/to/bitsquare-sn.init.sh /etc/init.d/bitsquare-sn
    # chmod a+rx /etc/init.d/bitsquare-sn
    # update-rc.d bitsquare-sn defaults
    # service bitsquare-sn start

Executing ``service bitsquare-sn status`` should report that the process is
running.

## Cron script

The attached [Cron script](bitsquare-sn.cron.sh) can be used to check the seed
node daemon periodically and restart it if it is using too much memory (RSS at
the time, may change to VSS later).

To enable this check, edit the script and change the ``MAX_RSS_MiB`` to
whatever limit (in MiB), copy it to ``/etc/cron.hourly`` and make it
executable:

    # cp /path/to/bitsquare-sn.cron.sh /etc/cron.hourly/bitsquare-sn
    # chmod a+rx /etc/cron.hourly/bitsquare-sn

The check will be run every hour.  For more sophisticated checks, use a proper
monitor like [Monit](https://mmonit.com/monit/).
