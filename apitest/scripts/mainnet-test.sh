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
  load 'version-parser'
  run ./bisq-cli --password="xyz" getversion
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "$CURRENT_VERSION" ]
}

@test "test getversion" {
  load 'version-parser'
  run ./bisq-cli --password=xyz getversion
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "$CURRENT_VERSION" ]
}

@test "test setwalletpassword \"a b c\"" {
  run ./bisq-cli --password=xyz setwalletpassword "a b c"
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
  run ./bisq-cli --password=xyz unlockwallet "a b c"
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: no unlock timeout specified" ]
}


@test "test unlockwallet \"a b c\" 8" {
  run ./bisq-cli --password=xyz unlockwallet "a b c" 8
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "wallet unlocked" ]
}

@test "test getbalance while wallet unlocked for 8s" {
  run ./bisq-cli --password=xyz getbalance
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "0.00000000" ]
  sleep 8
}

@test "test unlockwallet \"a b c\" 6" {
  run ./bisq-cli --password=xyz unlockwallet "a b c" 6
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
  run ./bisq-cli --password=xyz setwalletpassword "z z z"  "d e f"
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: incorrect old password" ]
}

@test "test setwalletpassword oldpwd newpwd" {
  run ./bisq-cli --password=xyz setwalletpassword "a b c"  "d e f"
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
  run ./bisq-cli --password=xyz removewalletpassword "d e f"
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "wallet decrypted" ]
  sleep 1
}

@test "test getbalance when wallet available & unlocked with 0 btc balance" {
  run ./bisq-cli --password=xyz getbalance
  [ "$status" -eq 0 ]
  echo "actual output:  $output" >&2
  [ "$output" = "0.00000000" ]
}

@test "test getfundingaddresses" {
  run ./bisq-cli --password=xyz getfundingaddresses
  [ "$status" -eq 0 ]
}

@test "test getaddressbalance missing address argument" {
  run ./bisq-cli --password=xyz getaddressbalance
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: no address specified" ]
}

@test "test getaddressbalance bogus address argument" {
  run ./bisq-cli --password=xyz getaddressbalance bogus
  [ "$status" -eq 1 ]
  echo "actual output:  $output" >&2
  [ "$output" = "Error: address bogus not found in wallet" ]
}

@test "test createpaymentacct PerfectMoneyDummy (missing name, nbr, ccy params)" {
  run ./bisq-cli --password=xyz createpaymentacct PERFECT_MONEY
  [ "$status" -eq 1 ]
 echo "actual output:  $output" >&2
  [ "$output" = "Error: incorrect parameter count, expecting payment method id, account name, account number, currency code" ]
}

@test "test createpaymentacct PERFECT_MONEY PerfectMoneyDummy 0123456789 USD" {
  run ./bisq-cli --password=xyz createpaymentacct PERFECT_MONEY PerfectMoneyDummy 0123456789 USD
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
  [ "$output" = "Error: incorrect parameter count, expecting direction (buy|sell), currency code" ]
}

@test "test getoffers sell eur check return status" {
  run ./bisq-cli --password=xyz getoffers sell eur
  [ "$status" -eq 0 ]
}

@test "test getoffers buy eur check return status" {
  run ./bisq-cli --password=xyz getoffers buy eur
  [ "$status" -eq 0 ]
}

@test "test getoffers sell gbp check return status" {
  run ./bisq-cli --password=xyz getoffers sell gbp
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
