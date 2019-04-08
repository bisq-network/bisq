#!/bin/sh

if [ "$SKIP_BUILD" != "true" ]; then
    gradle build
fi
