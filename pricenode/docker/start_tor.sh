#!/bin/bash

# sudo -u debian-tor
nohup sudo -u debian-tor tor > /dev/null 2>errors_tor.log &
