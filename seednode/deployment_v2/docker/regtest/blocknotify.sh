#!/bin/sh
echo $1 | nc -w 1 bisq-seednode-1 5120
echo $1 | nc -w 1 bisq-seednode-2 5121
