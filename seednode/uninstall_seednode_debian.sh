#!/bin/sh
echo "[*] Uninstalling Bitcoin and Bisq, will delete all data!!"
sleep 10
sudo rm -rf /root/bisq
sudo systemctl stop bitcoin
sudo systemctl stop bisq
sudo systemctl disable bitcoin
sudo systemctl disable bisq
sudo userdel -f -r bisq
sudo userdel -f -r bitcoin
echo "[*] Done!"
