# Building Bisq


## Clone

    git clone https://github.com/bisq-network/bisq
    cd bisq


## Build

You do _not_ need to install Gradle to complete the following command. The `gradlew` shell script will install it for you if necessary.

    ./gradlew build

If on Windows run `gradlew.bat build` instead.


## Run

Bisq executables are now available in the root project directory. Run Bisq Desktop as follows:

Note: bisq runs fine on jdk10 and jdk11. jdk12 is currently not supported. 

    ./bisq-desktop

If on Windows use the `bisq-desktop.bat` script instead.


## See also

 - [idea-import.md](idea-import.md)
 - [dev-setup.md](dev-setup.md)
