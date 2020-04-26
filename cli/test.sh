#!/bin/bash
#
# References & examples for expect:
#
#  - https://pantz.org/software/expect/expect_examples_and_tips.html
#  - https://stackoverflow.com/questions/13982310/else-string-matching-in-expect
#  - https://gist.github.com/Fluidbyte/6294378
#  - https://www.oreilly.com/library/view/exploring-expect/9781565920903/ch04.html
#
# Prior to running this script, run:
#
#     ./bisq-daemon --apiPassword=xyz
#
# The data directory used must contain an unencrypted wallet with a 0 BTC balance

# Ensure project root is the current working directory
cd $(dirname $0)/..

OUTPUT=$(expect -c '
    # exp_internal 1
    puts "TEST unsupported cmd error"
    set expected "Error: '\''bogus'\'' is not a supported method"
    spawn ./bisq-cli --password=xyz bogus
    expect {
        $expected { puts "PASS" }
        default {
            set results $expect_out(buffer)
            puts "FAIL expected = $expected"
            puts "       actual = $results"
        }
    }
')
echo "$OUTPUT"
echo "========================================================================"

OUTPUT=$(expect -c '
    puts "TEST unrecognized option error"
    set expected "Error: bogus is not a recognized option"
    spawn ./bisq-cli --bogus getversion
    expect {
        $expected { puts "PASS" }
        default {
            set results $expect_out(buffer)
            puts "FAIL expected = $expected"
            puts "       actual = $results"
        }
    }
')
echo "$OUTPUT"
echo "========================================================================"

OUTPUT=$(expect -c '
    # exp_internal 1
    puts "TEST missing required password option error"
    set expected "Error: Missing required option(s) \\\[password\\\]"
    spawn ./bisq-cli anymethod
    expect {
        $expected { puts "PASS" }
        default {
            set results $expect_out(buffer)
            puts "FAIL expected = $expected"
            puts "       actual = $results"
        }
    }
')
echo "$OUTPUT"
echo "========================================================================"

OUTPUT=$(expect -c '
    # exp_internal 1
    puts "TEST getversion (incorrect password error)"
    set expected "Error: incorrect '\''password'\'' rpc header value"
    spawn ./bisq-cli --password=bogus getversion
    expect {
        $expected { puts "PASS\n" }
        default {
            set results $expect_out(buffer)
            puts "FAIL expected = $expected"
            puts "       actual = $results"
        }
    }
')
echo "$OUTPUT"
echo "========================================================================"

OUTPUT=$(expect -c '
    # exp_internal 1
    puts "TEST getversion (password value in quotes) COMMIT"
    set expected "1.3.2"
    # Note: have to define quoted argument in a variable as "''value''"
    set pwd_in_quotes "''xyz''"
    spawn ./bisq-cli --password=$pwd_in_quotes getversion
    expect {
        $expected { puts "PASS" }
        default {
            set results $expect_out(buffer)
            puts "FAIL expected = $expected"
            puts "       actual = $results"
        }
    }
')
echo "$OUTPUT"
echo "========================================================================"

OUTPUT=$(expect -c '
    puts "TEST getversion"
    set expected "1.3.2"
    spawn ./bisq-cli --password=xyz getversion
    expect {
        $expected { puts "PASS" }
        default {
            set results $expect_out(buffer)
            puts "FAIL expected = $expected"
            puts "       actual = $results"
        }
    }
')
echo "$OUTPUT"
echo "========================================================================"

OUTPUT=$(expect -c '
    puts "TEST getbalance"
    # exp_internal 1
    set expected "0.00000000"
    spawn ./bisq-cli --password=xyz getbalance
    expect {
        $expected { puts "PASS" }
        default {
            set results $expect_out(buffer)
            puts "FAIL expected = $expected"
            puts "       actual = $results"
        }
    }
')
echo "$OUTPUT"

echo "========================================================================"

echo "TEST help (todo)"
./bisq-cli --password=xyz --help
