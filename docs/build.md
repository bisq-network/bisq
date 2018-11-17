# Building Bisq

_You will need [OpenJDK 10](https://jdk.java.net/10/) installed and set up as the default system JDK to complete the following instructions._


## Clone

    git clone https://github.com/bisq-network/bisq
    cd bisq


## Build

You do _not_ need to install Gradle to complete the following command. The `gradlew` shell script will install it for you if necessary.

    ./gradlew build


## Run

The Bisq executable jar is now available in the `desktop/build/libs/` directory. Run it as follows, replacing `{version}` with the actual version found in the filename:

    java -jar desktop/build/libs/desktop-{version}-all.jar


## See also

 - [idea-import.md](idea-import.md)
 - [dev-setup.md](dev-setup.md)
