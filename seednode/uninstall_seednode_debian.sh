#!/bin/sh
service bitcoin stop
service bisq stop
userdel bisq
rm -rf /root/bisq
userdel bitcoin
rm -rf /bitcoin
