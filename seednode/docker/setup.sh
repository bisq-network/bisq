#!/usr/bin/env bash

if [ "$SKIP_BUILD" != "true" ]; then
    ./gradlew build
fi
