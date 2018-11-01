Building From Source
====================

This guide will walk you through the process of building Bisq from source.

> _**NOTE:** For most users, building from source is not necessary. See the [releases page](https://github.com/bisq-network/bisq-desktop/releases), where you'll find installers for Windows, Linux and Mac OS X._

There is an install script (2 parts) for setup (JDK, Git, Bitcoinj, Bisq) on Linux in that directory (install_on_unix.sh, install_on_unix_fin.sh).

System requirements
-------------

The prerequisite for building Bisq is installing the Java Development Kit (JDK), version 8u131 or better (as well as Git).

    $ sudo apt-get install openjdk-8-jdk git

In Debian/Ubuntu with OpenJDK you'll need OpenJFX as well, i.e. you'll need the `openjfx` package besides the `openjdk-8-jdk` package.

    $ sudo apt-get install openjfx

### 1. Check the version of Java you currently have installed

    $ java -version

If `java` is not found, or your version is anything less than `1.8.0_121`, then follow the next steps, otherwise you can skip to step 2:

#### 1.1 Debian based systems (Ubuntu)

You can use either OpenJDK or Oracle JDK.

**To install OpenJDK use:**

    $ sudo apt-get install openjdk-8-jdk libopenjfx-java

Unfortunately, Ubuntu 14.04 & Linux Mint 17.3 are missing OpenJdk 8 and OpenJFX, so this might be useful:

If `openjdk-8-jdk` is not found you can add this ppa, update, then try again:

    $ sudo apt-add-repository ppa:openjdk-r/ppa && sudo apt-get install openjdk-8-jdk

If `libopenjfx-java` is not found you can build & install it yourself:

 * [How to install OpenJFX on Ubuntu 14.04 or Linux Mint 17.3](http://askubuntu.com/questions/833193/how-do-i-install-openjfx-on-ubuntu-14-04-linux-mint-17)

**To install the Oracle JDK use:**

    $ sudo add-apt-repository ppa:webupd8team/java
    $ sudo apt-get update
    $ sudo apt-get -y install oracle-java8-installer


**Check if $JAVA_HOME is set:**

    $ echo $JAVA_HOME

If `$JAVA_HOME` is not present, open your `.bashrc` file:

    $ touch ~/.bashrc
    $ gedit ~/.bashrc

* For OpenJDK add: `export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64`
* For Oracle JDK add: `export JAVA_HOME=/usr/lib/jvm/java-8-oracle`
* For your current *alternative* JDK add: `export JAVA_HOME=/usr/lib/jvm/default-java`
  (or `/usr/lib/jvm/default` for Arch or `/usr/lib/jvm/java` for Fedora)

Save and close the file.

Reload the file in your shell:

    $ . ~/.bashrc
    $ echo $JAVA_HOME

#### 1.2 Other systems

[Download and install the latest Oracle JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) for your platform.

For Mac OSX, you will need to set JAVA_HOME as:

    $ echo 'export JAVA_HOME=$(/usr/libexec/java_home)' >> ~/.bashrc
    $ . ~/.bashrc


### 2. If using Intellij install the Lombok plugin
https://plugins.jetbrains.com/plugin/6317-lombok-plugin

Build Bisq
-----------------

    $ git clone https://github.com/bisq-network/bisq-desktop.git
    $ cd bisq-desktop
    $ ./gradlew build

When the build completes, run Bisq with the following script:

    $ ./build/app/bin/bisq-desktop

Build binaries
-----------------

If you want to build the binaries check out the build scripts under the package directory. Use the shaded.jar and the lib directory.


DAO full node
-----------------
If you want to run your own BSQ transaction verification node you have to run Bitcoin Core with RPC enabled and
use dedicated program arguments for the Bisq node.
See https://github.com/bisq-network/bisq-desktop/blob/master/doc/rpc.md for more details.


Development mode
-----------------

Please check out our wiki for more information about [testing](https://github.com/bisq-network/bisq-desktop/wiki/4.3.-Testing-Bisq-with-Testnet)
and how to use [regtest](https://github.com/bisq-network/bisq-desktop/wiki/4.2.1.-How-to-use-bisq-with-regtest-%28advanced%29)

Here are example program arguments for using regtest with localhost environment (not using Tor):

    $ bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhost=true --myAddress=localhost:2222 --nodePort=2222 --appName=bisq-Local-Regtest-Arbitrator

    $ bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhost=true --myAddress=localhost:3333 --nodePort=3333 --appName=bisq-Local-Regtest-Alice

    $ bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhost=true --myAddress=localhost:4444 --nodePort=4444 --appName=bisq-Local-Regtest-Bob


Running local seed node with Tor and RegTest
-----------------

See the documentation at https://github.com/bisq-network/bisq-seednode


Problems?
---------

If the instructions above don't work for you, please [raise an issue](https://github.com/bisq-network/bisq-desktop/issues/new?labels=%5Bbuild%5D). Thanks!
