#!/usr/bin/env bash
#
# Answers one HTTP request of the price feed client with fee data and no market prices.
# entrypoint.sh runs it per connection with ncat on 127.0.0.1, so the daemons have fee data
# as on mainnet. Without it each version keeps its own default fee rate and peers of
# different versions reject each other's trade tx fee.
#
# 5 sat/vbyte is the default of the current tree, so its rates do not change.

# Read the request headers before answering, so closing the socket does not reset it.
while IFS= read -r line; do
  [[ "${line}" == $'\r' || -z "${line}" ]] && break
done

body="{\"data\":[],\"bitcoinFeesTs\":$(date +%s),\"bitcoinFeeInfo\":{\"btcTxFee\":5,\"btcMinTxFee\":5}}"
printf 'HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s' \
  "${#body}" "${body}"
