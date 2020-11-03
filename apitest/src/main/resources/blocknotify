#!/bin/bash

# Regtest ports start with 512*

# To avoid pesky bitcoind io errors, do not specify ports Bisq is not listening to.

# SeedNode listens on port 5120
echo $1 | nc -w 1 127.0.0.1 5120

# Arb Node listens on port 5121
echo $1 | nc -w 1 127.0.0.1 5121

# Alice Node listens on port 5122
echo $1 | nc -w 1 127.0.0.1 5122

# Bob Node listens on port 5123
echo $1 | nc -w 1 127.0.0.1 5123

# Some other node listens on port 5124, etc.
# echo $1 | nc -w 1 127.0.0.1 5124
