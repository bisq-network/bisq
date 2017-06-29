#!/bin/sh
# kFreeBSD do not accept scripts as interpreters, using #!/bin/sh and sourcing.
if [ true != "$INIT_D_SCRIPT_SOURCED" ] ; then
    set "$0" "$@"; INIT_D_SCRIPT_SOURCED=true . /lib/init/init-d-script
fi
### BEGIN INIT INFO
# Provides:          bisq-sn
# Required-Start:    $local_fs $remote_fs $named $network $time
# Required-Stop:     $local_fs $remote_fs $named $network $time
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: bisq seed node
# Description:       This script manages the execution of
#                    a bisq seed node process using
#                    its own Tor node to provide a hidden service.
### END INIT INFO

# Author: Ivan Vilata-i-Balaguer <ivan@selidor.net>


## BEGIN CONFIGURATION
# HOST:PORT address of seed node.  Change this for each instance.
SN_ADDRESS=1a2b3c4d5e6f7g8h.onion:8000

# Bitcoin network: 0=mainnet, 1=testnet, 2=regtest.
SN_NETWORK_ID=0
# Maximum number of connecitions to allow.
SN_MAX_CONNECTIONS=100

# Location of the seed node jar file.  Use to select a particular version.
SN_JAR=~bsqsn/SeedNode-0.4.4.jar
# User to run the seed node as.
SN_USER=bsqsn
## END CONFIGURATION


# Using a name different than the daemon's base name
# causes problems when stopping the process.
#NAME="bisq-sn"
DESC="bisq seed node $SN_ADDRESS"
START_ARGS="--chuid $SN_USER --background --make-pidfile"
PIDFILE="/var/run/bisq-sn/$SN_ADDRESS.pid"
DAEMON=/usr/bin/java
DAEMON_ARGS="-jar $SN_JAR $SN_ADDRESS $SN_NETWORK_ID $SN_MAX_CONNECTIONS"

do_start_prepare() {
    mkdir -p "$(dirname "$PIDFILE")"
}
