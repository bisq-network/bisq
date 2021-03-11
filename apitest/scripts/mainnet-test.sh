#!/usr/bin/env bats
#
# Smoke tests for bisq-cli running against a live bisq-daemon (on mainnet)
#
# Prerequisites:
#
#  - bats-core 1.2.0+ must be installed (brew install bats-core on macOS)
#    see https://github.com/bats-core/bats-core
#
#  - Run `./bisq-daemon --apiPassword=xyz --appDataDir=$TESTDIR` where $TESTDIR
#    is empty or otherwise contains an unencrypted wallet with a 0 BTC balance
#
# Usage:
#
#  This script must be run from the root of the project, e.g.:
#
#     bats apitest/scripts/mainnet-test.sh

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
  [ "$output" = "Error: missing required 'password' option" ]
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
  load 'version-parser'
  run ./bisq-cli --password="xyz" getversion
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "$CURRENT_VERSION" ]
}

@test "test getversion" {
  # Wait 1 second before calling getversion again.
  sleep 1
  load 'version-parser'
  run ./bisq-cli --password=xyz getversion
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "$CURRENT_VERSION" ]
}

@test "test setwalletpassword \"a b c\"" {
  run ./bisq-cli --password=xyz setwalletpassword --wallet-password="a b c"
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "wallet encrypted" ]
  sleep 1
}

@test "test unlockwallet without password & timeout args" {
  run ./bisq-cli --password=xyz unlockwallet
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: no password specified" ]
}

@test "test unlockwallet without timeout arg" {
  run ./bisq-cli --password=xyz unlockwallet --wallet-password="a b c"
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: no unlock timeout specified" ]
}


@test "test unlockwallet \"a b c\" 8" {
  run ./bisq-cli --password=xyz unlockwallet --wallet-password="a b c" --timeout=8
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "wallet unlocked" ]
}

@test "test getbalance while wallet unlocked for 8s" {
  run ./bisq-cli --password=xyz getbalance
  [ "$status" -eq 0 ]
  sleep 8
}

@test "test unlockwallet \"a b c\" 6" {
  run ./bisq-cli --password=xyz unlockwallet --wallet-password="a b c" --timeout=6
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "wallet unlocked" ]
}

@test "test lockwallet before unlockwallet timeout=6s expires" {
  run ./bisq-cli --password=xyz lockwallet
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "wallet locked" ]
}

@test "test setwalletpassword incorrect old pwd error" {
  run ./bisq-cli --password=xyz setwalletpassword --wallet-password="z z z"  --new-wallet-password="d e f"
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: incorrect old password" ]
}

@test "test setwalletpassword oldpwd newpwd" {
  # Wait 5 seconds before calling setwalletpassword again.
  sleep 5
  run ./bisq-cli --password=xyz setwalletpassword --wallet-password="a b c"  --new-wallet-password="d e f"
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "wallet encrypted with new password" ]
  sleep 1
}

@test "test getbalance wallet locked error" {
  run ./bisq-cli --password=xyz getbalance
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: wallet is locked" ]
}

@test "test removewalletpassword" {
  run ./bisq-cli --password=xyz removewalletpassword --wallet-password="d e f"
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "wallet decrypted" ]
  sleep 3
}

@test "test getbalance when wallet available & unlocked with 0 btc balance" {
  run ./bisq-cli --password=xyz getbalance
  [ "$status" -eq 0 ]
}

@test "test getfundingaddresses" {
  run ./bisq-cli --password=xyz getfundingaddresses
  [ "$status" -eq 0 ]
}

@test "test getunusedbsqaddress" {
  run ./bisq-cli --password=xyz getunusedbsqaddress
  [ "$status" -eq 0 ]
}

@test "test getaddressbalance missing address argument" {
  run ./bisq-cli --password=xyz getaddressbalance
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: no address specified" ]
}

@test "test getaddressbalance bogus address argument" {
  # Wait 1 second before calling getaddressbalance again.
  sleep 1
  run ./bisq-cli --password=xyz getaddressbalance --address=bogus
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: address bogus not found in wallet" ]
}

@test "test getpaymentmethods" {
  run ./bisq-cli --password=xyz getpaymentmethods
  [ "$status" -eq 0 ]
}

@test "test getpaymentaccts" {
  run ./bisq-cli --password=xyz getpaymentaccts
  [ "$status" -eq 0 ]
}

@test "test getoffers missing direction argument" {
  run ./bisq-cli --password=xyz getoffers
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: no direction (buy|sell) specified" ]
}

@test "test getoffers sell eur check return status" {
  # Wait 1 second before calling getoffers again.
  sleep 1
  run ./bisq-cli --password=xyz getoffers --direction=sell --currency-code=eur
  [ "$status" -eq 0 ]
}

@test "test getoffers buy eur check return status" {
  # Wait 1 second before calling getoffers again.
  sleep 1
  run ./bisq-cli --password=xyz getoffers --direction=buy --currency-code=eur
  [ "$status" -eq 0 ]
}

@test "test getoffers sell gbp check return status" {
  # Wait 1 second before calling getoffers again.
  sleep 1
  run ./bisq-cli --password=xyz getoffers --direction=sell --currency-code=gbp
  [ "$status" -eq 0 ]
}

@test "test help displayed on stderr if no options or arguments" {
  run ./bisq-cli
  [ "$status" -eq 1 ]
  [ "${lines[0]}" = "Bisq RPC Client" ]
  [ "${lines[1]}" = "Usage: bisq-cli [options] <method> [params]" ]
  # TODO add asserts after help text is modified for new endpoints
}

@test "test --help option" {
  run ./bisq-cli --help
  [ "$status" -eq 0 ]
  [ "${lines[0]}" = "Bisq RPC Client" ]
  [ "${lines[1]}" = "Usage: bisq-cli [options] <method> [params]" ]
  # TODO add asserts after help text is modified for new endpoints
}

@test "test takeoffer method --help" {
  run ./bisq-cli --password=xyz takeoffer --help
  [ "$status" -eq 0 ]
  [ "${lines[0]}" = "takeoffer" ]
}
