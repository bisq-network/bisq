#!/bin/bash
echo "DANGER! This will nuke your bitcoin regtest dir and bisq seed, alice and bob regtest dirs as well!"
echo "Press Enter to continue, otherwise ctrl+c"
read
rm blocks regtest -rf
rm ~/.local/share/bisq-BTC_REGTEST_Seed_2002 -rf
rm ~/.local/share/bisq-BTC_REGTEST_Alice -rf
rm ~/.local/share/bisq-BTC_REGTEST_Bob -rf
sed -r -i 's/dao=.*/dao="false"/' bisq/config.sh
echo "cleaned bitcoind home and bisq seed, alice, bob instances. The conf file for bisq was reset to dao=false"
