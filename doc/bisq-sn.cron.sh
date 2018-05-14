#!/bin/sh
# Restart Bisq seed node daemons whose resident memory (RSS)
# is over MAX_RSS_MiB.
#
# Scripts in the INITDIR must contain a ``SN_ADDRESS=<host:port>``
# assignment.
#
# Exit with status 2 if there were restarted daemons.
#
# Author: Ivan Vilata-i-Balaguer <ivan@selidor.net>
MAX_RSS_MiB=400

PIDDIR=/var/run/bisq-sn
INITDIR=/etc/init.d

# Restart the daemon with the given address.
restart() {
    rcscript=$(grep -El "^SN_ADDRESS=['\"]?$1['\"]?" $INITDIR/*)
    if [ "$rcscript" ]; then
        "$rcscript" restart
    fi
}

restarted=
max_rss_kib=$((MAX_RSS_MiB*1024))
for pidfile in $PIDDIR/*.pid; do
    address=$(basename ${pidfile%.pid})
    pid=$(cat "$pidfile")
    test "$pid" || continue
    rss_kib=$(ps -o rss= "$pid")
    test "$rss_kib" || continue
    if [ "$rss_kib" -gt "$max_rss_kib" ]; then
        echo "bisq seed node $address ($((rss_kib/1024))M) surpassed memory limit of ${MAX_RSS_MiB}M, restarting." >&2
        restart $address
        restarted=y
    fi
done

if [ "$restarted" ]; then
    exit 2
else
    exit 0
fi
