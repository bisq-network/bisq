#!/bin/bash

# Bats helper script for parsing current version from Version.java.

export CURRENT_VERSION=$(grep "String VERSION =" common/src/main/java/bisq/common/app/Version.java | sed 's/[^0-9.]*//g')
