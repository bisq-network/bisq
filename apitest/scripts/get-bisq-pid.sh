#!/bin/bash

# Find the pid of the java process by grepping for the mainClassName and appName,
# then print the 2nd column of the output to stdout.
#
# Doing this from Java is problematic, probably due to limitation of the
# apitest.linux.BashCommand implementation.


MAIN_CLASS_NAME=$1
APP_NAME=$2

# TODO args validation

ps aux | grep java | grep "${MAIN_CLASS_NAME}" | grep "${APP_NAME}" | awk '{print $2}'
