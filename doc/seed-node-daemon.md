# Running a seed node as a daemon

This document presents some steps to be able to run a bisq seed node as
an unattended daemon in a GNU/Linux server with a traditional System V init.

## Before you start

We assume that you have already configured a bisq seed node to run in a
computer.  You will need to upload the seed node code and configuration to the
server:

  - The code is contained in the ``SeedNode.jar`` file which is usually left
    under ``seednode/target`` after building bisq.
  - The seed node configuration is the ``bisq_seed_node_HOST_PORT``
    directory under ``~/.local/share`` (Unix), ``%APPDATA%`` (Windows) or
    ``~/Library/Application Support`` (Mac OS X).

## Dedicated user

In order to avoid security risks, it is highly recommended that you create a
dedicated user in the server to run the seed node daemon, and that you forbid
other users to access its files, for instance:

    # adduser bsqsn
    # chmod go-rwx ~bsqsn

Place the jar file where the ``bsqsn`` user can read it and tag it with
bisq's version number (to allow running several instances of mutually
incompatible versions), e.g. ``~bsqsn/SeedNode-VERSION.jar``.  Copy the
configuration directory to the ``~bsqsb/.local/share``  directory.

## Testing the seed node

You need to check that the seed node can actually run in your system.  For
instance, if you are using version 0.4.4 and your seed node's Tor address is
``1a2b3c4d5e6f7g8h.onion:8000``, try to run this as the ``bsqsn`` user:

    $ java -jar ~bsqsn/SeedNode-0.4.4.jar 1a2b3c4d5e6f7g8h.onion:8000 0 50

Please check error messages if it fails to run.  Do note that you will need
OpenJDK and OpenJFX in the server.  In Debian-like systems you may install the
needed dependencies with:

    # apt-get --no-install-recommends install openjfx

After the node runs successfully, interrupt it with Control-C.

## Init script

To allow the daemon to start automatically on system boot, use the attached
[init script](bisq-sn.init.sh).  First edit it and change its
configuration variables to your needs, especially ``SN_ADDRESS``, ``SN_JAR``
and ``SN_USER``.  In the previous example, the values would be:

    SN_ADDRESS=1a2b3c4d5e6f7g8h.onion:8000
    SN_JAR=~bsqsn/SeedNode-0.4.4.jar
    SN_USER=bsqsn

Put the customized script under ``/etc/init.d`` using a name without
extensions (e.g. ``bisq-sn``), make it executable, add it to the boot
sequence and finally start it:

    # cp /path/to/bisq-sn.init.sh /etc/init.d/bisq-sn
    # chmod a+rx /etc/init.d/bisq-sn
    # update-rc.d bisq-sn defaults
    # service bisq-sn start

Executing ``service bisq-sn status`` should report that the process is
running.

## Cron script

The attached [Cron script](bisq-sn.cron.sh) can be used to check the seed
node daemon periodically and restart it if it is using too much memory (RSS at
the time, may change to VSS later).

To enable this check, edit the script and change the ``MAX_RSS_MiB`` to
whatever limit (in MiB), copy it to ``/etc/cron.hourly`` and make it
executable:

    # cp /path/to/bisq-sn.cron.sh /etc/cron.hourly/bisq-sn
    # chmod a+rx /etc/cron.hourly/bisq-sn

The check will be run every hour.  For more sophisticated checks, use a proper
monitor like [Monit](https://mmonit.com/monit/).

## Monitor script

The attached [monitor script](monitor-bisq-sn.cron.sh) can be used to
watch several seed nodes by connecting to them over Tor, and report by email
if there were any failed connection attempts.  The script uses the ``torify``
and ``nc``  tools, so make sure that you have the ``tor`` and some ``netcat``
package installed in your system.  Also make sure that it is able to send
messages using the ``mail``  utility.

To enable the monitor, first edit the script and set the email addresses you
want to report to in ``REPORT_TO_EMAILS``; if you want to specify the set of
seed nodes to check, change the value of ``SEED_NODES``.  Then copy the script
to ``/etc/cron.hourly`` and make it executable:

    # cp /path/to/monitor-bisq-sn.cron.sh /etc/cron.hourly/monitor-bisq-sn
    # chmod a+rx /etc/cron.hourly/monitor-bisq-sn

Since this script requires no special permissions, you may instead want to run
it from a normal user's crontab (e.g. the ``bsqsn`` user above).
