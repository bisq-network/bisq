#!/bin/bash

cd /home/bs/.local/share

find . -name \*.DS_Store -type f -delete
find . -name \*._.DS_Store -type f -delete
find . -name \*SequenceNumberMap -type f -delete
find . -name \*PersistedPeers -type f -delete
find . -name \*bisq.log -type f -delete
find . -name \*__MACOSX -type d -delete
find . -name \*backup -type d -exec rm -R -f {} +
