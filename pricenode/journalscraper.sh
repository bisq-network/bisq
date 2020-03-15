#!/bin/bash

HOSTNAME="${COLLECTD_HOSTNAME:-localhost}"
INTERVAL=750

last=$(date +"%F %T" -d "$INTERVAL seconds ago")
while true;
do
	now=$(date +"%F %T")

	journalctl -u bisq-pricenode --since="$last" --until="$now" | grep -Eo "getAllMarketPrices.*bisq/[0-9].[0-9].[0-9]" | cut -d / -f 2 | sort | uniq -c | while read -r line; do
		number=$(echo "${line}" | cut -d ' ' -f 1);
		version=$(echo "${line}" | cut -d \  -f 2);
		version=${version//./_};
		echo "PUTVAL $HOSTNAME/requestsPer750Seconds/gauge-v$version interval=$INTERVAL N:$number";
	done
	last=$now

	sleep $INTERVAL
done
