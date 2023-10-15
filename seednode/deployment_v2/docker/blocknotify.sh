#!/bin/sh
echo $1 | nc -w 1 bisq-seednode 5120
