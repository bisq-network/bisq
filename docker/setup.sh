#!/usr/bin/env bash

if [ "$SKIP_BUILD" != "true" ]; then
    mvn dependency:resolve compile
fi
