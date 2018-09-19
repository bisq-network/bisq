It is highly recommended that you start the whole environment (alice,bob,etc.) using:

    docker-compose up -d alice bob arbitrator

Some of the scripts use `jq` tool.

To install it on Debian:

     apt-get update && apt-get install -y jq

To install jq on Gentoo:

    emerge jq
