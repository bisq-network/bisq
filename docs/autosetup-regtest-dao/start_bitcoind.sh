#!/bin/bash
./generate_101_blocks.sh &
bitcoin-qt -datadir=. #-printtoconsole 
