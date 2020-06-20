#!/usr/bin/env bats
#
# Integration tests for bisq-cli running against a live bisq-daemon
#
# Prerequisites:
#
#  - bats v0.4.0 must be installed (brew install bats on macOS)
#    see https://github.com/sstephenson/bats/tree/v0.4.0
#
#  - Run `./bisq-daemon --apiPassword=xyz --appDataDir=$TESTDIR` where $TESTDIR
#    is empty or otherwise contains an unencrypted wallet with a 0 BTC balance
#
# Usage:
#
#  This script must be run from the root of the project, e.g.:
#
#     ./cli/test.sh

@test "test unsupported method error" {
  run ./bisq-cli --password=xyz bogus
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2  # printed only on test failure
  [ "$output" = "Error: 'bogus' is not a supported method" ]
}

@test "test unrecognized option error" {
  run ./bisq-cli --bogus getversion
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: bogus is not a recognized option" ]
}

@test "test missing required password option error" {
  run ./bisq-cli getversion
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: missing required 'password' option" ]
}

@test "test incorrect password error" {
  run ./bisq-cli --password=bogus getversion
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: incorrect 'password' rpc header value" ]
}

@test "test getversion call with quoted password" {
  run ./bisq-cli --password="xyz" getversion
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "1.3.2" ]
}

@test "test getversion" {
  run ./bisq-cli --password=xyz getversion
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "1.3.2" ]
}

@test "test getbalance (available & unlocked wallet with 0 btc balance)" {
  run ./bisq-cli --password=xyz getbalance
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "0.00000000" ]
}

@test "test help displayed on stderr if no options or arguments" {
  run ./bisq-cli
  [ "$status" -eq 1 ]
  [ "${lines[0]}" = "Bisq RPC Client" ]
  [ "${lines[1]}" = "Usage: bisq-cli [options] <method>" ]
  # TODO add asserts after help text is modified for new endpoints
}

@test "test --help option" {
  run ./bisq-cli --help
  [ "$status" -eq 0 ]
  [ "${lines[0]}" = "Bisq RPC Client" ]
  [ "${lines[1]}" = "Usage: bisq-cli [options] <method>" ]
  # TODO add asserts after help text is modified for new endpoints
}
