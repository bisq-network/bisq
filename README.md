# Bisq

[![Build Status](https://travis-ci.org/bisq-network/bisq.svg?branch=master)](https://travis-ci.org/bisq-network/bisq)


## What is Bisq?

Bisq is a safe, private and decentralized way to exchange bitcoin for national currencies and other cryptocurrencies. Bisq uses peer-to-peer technology and multi-signature escrow to facilitate trading without the need for a centralized third party exchange. Bisq is non-custodial (never holds your funds), and incorporates a human arbitration system to resolve disputes.

For more information, see https://bisq-network/intro and for step-by-step getting started instructions, see https://bisq.network/get-started.


## Building Bisq

You will need [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) installed to complete the following instructions.

1. Clone the Bisq source code and cd into `bisq`

        git clone https://github.com/bisq-network/bisq
        cd bisq

2. Build Bisq

    You do _not_ need to install Gradle to complete the following command. The `gradlew` shell script will install it for you if necessary.

        ./gradlew build


## Running Bisq

With the above build complete, the Bisq executable jar is now available in the `desktop/build/libs/` directory. Run it as follows, replacing `{version}` with the actual version found in the filename:

    java -jar desktop/build/libs/desktop-{version}-all.jar`


## Importing Bisq into Intellij IDEA

_The following instructions have been tested on IDEA 2017.3_

 1. Open IDEA
 1. Go to `Help->Edit Custom Properties...`, add a line to the file that reads `idea.max.intellisense.filesize=12500` (to handle Bisq's very large generated `PB.java` Protobuf source file)
 1. Go to `Preferences->Plugins`. Search for and install the _Lombok_ plugin. When prompted, do not restart IDEA.
 1. Go to `Preferences->Build, Execution, Deployment->Compiler->Annotation Processors` and check the `Enable annotation processing` option (to enable processing of Lombok annotations)
 1. Restart IDEA
 1. Go to `Import Project`, select `settings.gradle` and click `Open`
 1. In the `Import Project from Gradle` screen, check the `Use auto-import` option and click `OK`
 1. When prompted whether to overwrite the existing `.idea` directory, click `Yes`
 1. In the `Project` tool window, right click on the root-level `.idea` folder, select `Git->Revert...` and click OK in the dialog that appears (to restore source-controlled `.idea` configuration files that get overwritten during project import)
 1. Go to `Build->Build project`. Everything should build cleanly. You should be able to run tests, run `main` methods in any component, etc.

> TIP: If you encounter compilation errors related to the `io.bisq.generated.protobuffer.PB` class, it is probably because you didn't run the full Gradle build above. You need to run the `generateProto` task in the `common` project. You can do this via the Gradle tool window in IDEA, or you can do it the command line with `cd common; ./gradlew generateProto`. Once you've done that, run `Build->Build project` again and you should have no errors.

