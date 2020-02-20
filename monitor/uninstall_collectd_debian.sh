#!/bin/sh
echo "[*] Stopping Bisq Server monitoring utensils"
echo '  '
echo 'This script will not remove any configuration or binaries from the system. It just stops the services.'

sleep 10
sudo systemctl stop nginx
sudo systemctl stop collectd
sudo systemctl disable nginx
sudo systemctl disable collectd
echo "[*] Done!"
