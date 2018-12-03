#!/usr/bin/env bash
#
# Install dependencies necessary for releasing Bisq on Debian-like systems.
#
cd $(dirname ${0})
set -eu

echo "Updating OS.."
sudo apt-get update
sudo apt-get upgrade
sudo apt-get dist-upgrade

echo "Done."
