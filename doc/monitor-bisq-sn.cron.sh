#!/bin/sh
# Report by email failing connections to bisq seed nodes.
#
# Exit with status 2 if unreachable nodes were detected.
#
# You may drop this under ``/etc/cron.hourly``.
# Please make sure that your system can send emails.
#
# Requirements: coreutils, tor, netcat, mail.
#
# Author: Ivan Vilata-i-Balaguer <ivan@selidor.net>


## BEGIN CONFIGURATION
# Email addresses to report failing nodes to, white-separated.
REPORT_TO_EMAILS='
bsqsn@example.com
'

# Seed node addresses to check as ONION_ADDRESS:PORT, white-separated.
# Addresses from ``SeedNodesRepository`` v0.4.4.
SEED_NODES='
uadzuib66jupaept.onion:8000
hbma455xxbqhcuqh.onion:8000
wgthuiqn3aoiovbm.onion:8000
2zxtnprnx5wqr7a3.onion:8000
'
## END CONFIGURATION


failing_seed_nodes=''
for sn in $SEED_NODES; do
    torify nc -z $(echo "$sn" | tr ':' ' ') > /dev/null 2>&1
    if [ $? != 0 ]; then
        failing_seed_nodes="$failing_seed_nodes $sn"
    fi
done

if [ "$failing_seed_nodes" ]; then
    cat <<- EOF | mail -s "Failing bisq seed nodes" -- $REPORT_TO_EMAILS
	The following bisq seed nodes failed to accept a new connection:

	$(echo $failing_seed_nodes | tr ' ' '\n' | sed 's/^/    /')

	Please check if they have issues, thank you.

	-- 
	The bisq seed monitor running at $(hostname -f)
	EOF
	exit 2
fi
