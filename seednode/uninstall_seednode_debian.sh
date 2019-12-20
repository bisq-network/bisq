#!/bin/sh
echo "[*] Uninstalling Bitcoin and Bisq, will delete all data!!"
sudo rm -rf /root/bisq
sudo systemctl stop bitcoin
sudo systemctl stop bisq
sudo systemctl disable bitcoin
sudo systemctl disable bisq
sleep 10
sudo userdel -f -r bisq
sleep 10
sudo userdel -f -r bitcoin
echo "[*] Done!"
