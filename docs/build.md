# Building Bisq


## Install Git LFS

Bisq uses Git LFS to track certain large binary files. Follow the instructions at https://git-lfs.github.com to install it, then run the following to command to verify the installation:

    $ git lfs version
    git-lfs/2.10.0 (GitHub; darwin amd64; go 1.13.6)
    

## Clone

    git clone https://github.com/bisq-network/bisq
    cd bisq


## Build

You do _not_ need to install Gradle to complete the following command. The `gradlew` shell script will install it for you if necessary. Pull the lfs data first.

    git lfs pull
    ./gradlew build

If on Windows run `gradlew.bat build` instead.
If in need to install JAVA check out - https://github.com/bisq-network/bisq/tree/master/scripts

## Run

Bisq executables are now available in the root project directory. Run Bisq Desktop as follows:

Note: bisq runs fine on jdk10 and jdk11. jdk12 is currently not supported.

    ./bisq-desktop

If on Windows use the `bisq-desktop.bat` script instead.
If in need to install JAVA checkout the install_java scripts at https://github.com/bisq-network/bisq/tree/master/scripts

## See also

 - [idea-import.md](idea-import.md)
 - [dev-setup.md](dev-setup.md)
