# Building Bisq

_You will need [OpenJDK 10](https://jdk.java.net/10/) installed and set up as the default system JDK to complete the following instructions._


## Clone

    git clone https://github.com/bisq-network/bisq
    cd bisq


## Build

You do _not_ need to install Gradle to complete the following command. The `gradlew` shell script will install it for you if necessary.

    ./gradlew build

If on Windows use the `gradlew.bat` script instead.


## Run

Bisq executables are now available in the root project directory. Run Bisq Desktop as follows:

    ./bisq-desktop

If on Windows use the `bisq-desktop.bat` script instead.


## See also

 - [idea-import.md](idea-import.md)
 - [dev-setup.md](dev-setup.md)
