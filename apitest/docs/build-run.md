# Build and Run API

The Java based API runs on Linux and OSX.

## Mainnet

To build from the source, clone the github repository found at `https://github.com/bisq-network/bisq`,
and build with gradle:

    $ ./gradlew clean build

To skip tests:

    $ ./gradlew clean build -x test

To run the Bisq daemon:

    $ ./bisq-daemon --apiPassword=<api-password> --apiPort=<api-port(default=9998)> --appDataDir=$APPDIR`

_Note: `$APPDIR` is empty or otherwise contains a Bisq wallet created by the daemon or the UI._

_Note: Never run the API daemon and Bisq UI on the same host, or you will corrupt your wallet.  It will be possible
with specific command line options, i.e., unique appDatadir and ports, but this scenario has not been tested yet._

## Test Harness

The API test harness uses the GNU Bourne-Again SHell `bash`, and is not supported on Windows.

### Predefined DAO / Regtest Setup

The API test harness depends on the contents of https://github.com/bisq-network/bisq/raw/master/docs/dao-setup.zip.
The files contained in dao-setup.zip include a bitcoin-core wallet, a regtest genesis tx and chain of 111 blocks, plus
data directories for Bob and Alice Bisq instances.  Bob & Alice wallets are pre-configured with 10 BTC each, and the
equivalent of 2.5 BTC in BSQ distributed among Bob & Alice's BSQ wallets.

See https://github.com/bisq-network/bisq/blob/master/docs/dao-setup.md for details.

### Install DAO / Regtest Setup Files

Bisq's gradle build file defines a task for downloading dao-setup.zip and extracting its contents to the
`apitest/src/main/resources` folder, and the test harness will install a fresh set of data files to the
`apitest/build/resources/main` folder during a test case's scaffold setup phase -- normally a static `@BeforeAll` method.

The dao-setup files can be downloaded during a normal build:

    $ ./gradlew clean build :apitest:installDaoSetup

Or by running a single task:

    $ ./gradlew :apitest:installDaoSetup

The `:apitest:installDaoSetup` task does not need to be run again until after the next time you run the gradle `clean` task.

### Run API Tests

The API test harness supports narrow & broad functional and full end to end test cases requiring
long setup and teardown times -- for example, to start a bitcoind instance, seednode, arbnode, plus Bob & Alice
Bisq instances, then shut everything down in proper order.  For this reason, API test cases do not run during a normal
gradle build.

To run API test cases, pass system property`-DrunApiTests=true`.

To run all existing test cases:

    $ ./gradlew  :apitest:test -DrunApiTests=true

To run all test cases in a package:

    $ ./gradlew  :apitest:test --tests "bisq.apitest.method.*" -DrunApiTests=true

To run a single test case:

    $ ./gradlew  :apitest:test --tests "bisq.apitest.scenario.WalletTest" -DrunApiTests=true

To run test cases from Intellij, add two JVM arguments to your JUnit launchers:

    -DrunApiTests=true -Dlogback.configurationFile=apitest/build/resources/main/logback.xml

The `-Dlogback.configurationFile` property will prevent `logback` from printing warnings about multiple `logback.xml`
files it will find in Bisq jars `cli.jar`, `daemon.jar`, and `seednode.jar`.

### Gradle Test Reports

To see detailed test results, logs, and full stack traces for test failures, open
`apitest/build/reports/tests/test/index.html` in a browser.

### See also

 - [test-categories.md](test-categories.md)

