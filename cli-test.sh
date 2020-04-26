#!/bin/bash
#
# Some references & examples for expect:
# https://pantz.org/software/expect/expect_examples_and_tips.html
# https://stackoverflow.com/questions/13982310/else-string-matching-in-expect
# https://gist.github.com/Fluidbyte/6294378
# https://www.oreilly.com/library/view/exploring-expect/9781565920903/ch04.html
#
# Requirements for these test cases:
#
#    :daemon -> ./bisq-daemon --apiPassword=xyz
#    :cli -> unencrypted wallet with balance = 0.00000000 BTC
#    test script must be copied to bisq project root dir
#    usage:   ./cli-test.sh

OUTPUT=$(expect -c '
    # exp_internal 1
    puts "TEST unsupported cmd error"
    set expected "Error: '\''peanutbutter'\'' is not a supported method"
    spawn  ./bisq-cli --password=xyz peanutbutter
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
    puts "TEST bad option error"
    set expected "Error: pwd is not a recognized option"
    spawn ./bisq-cli --pwd=xyz  getversion
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
    puts "TEST getversion (no pwd error)"
    set expected "Error: rpc server password not specified"
    spawn  ./bisq-cli getversion
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
    puts "TEST getversion (bad pwd error)"
    set expected "Error: incorrect '\''password'\'' rpc header value"
    spawn ./bisq-cli --password=badpassword  getversion
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
    puts "TEST getversion (pwd in quotes) COMMIT"
    set expected "1.3.2"
    # Note:  have to define quoted argument in a variable as "''value''"
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
    spawn ./bisq-cli --password=xyz  getversion
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
    puts "TEST getbalance (no pwd error)"
    # exp_internal 1
    set expected "Error: rpc server password not specified"
    spawn  ./bisq-cli getbalance
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
    spawn  ./bisq-cli --password=xyz getbalance
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
./bisq-cli --password=xyz  --help
