Building From Source
====================

This guide will walk you through the process of building Bitsquare from source.

> _**NOTE:** For most users, building from source is not necessary. See the [releases page](https://github.com/bitsquare/bitsquare/releases), where you'll find installers for Windows, Linux and Mac OS X._


For the impatient
-----------------

What follows is explained in detail in the sections below, but for those who know their way around Java, git and Gradle, here are the instructions in a nutshell:

    $ javac -version
    javac 1.8.0_20       # must be 1.8.0_20 or better

    $ git clone https://github.com/bitsquare/bitsquare.git
    $ cd bitsquare
    $ ./gradlew build    # (on *nix)
       --- or ---
    $ gradlew build      # (on Windows)

When the build completes, you will find executables and installers specific to your platform in the `build/distributions/` directory.


Prerequisites
-------------

The only prerequisite for building Bitsquare is installing the Java Development Kit (JDK), version 8u20 or better.

To check the version of Java you currently have installed:

    $ javac -version
    javac 1.8.0_20

If `javac` is not found, or your version is anything less than `1.8.0_20`, then you'll need to [download and install the latest JDK]( http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) for your platform.

> _**TIP:** Here are [instructions](http://www.webupd8.org/2014/03/how-to-install-oracle-java-8-in-debian.html) for installing the JDK via `apt` on Debian/Ubuntu systems._


Steps
-----

### 1. Get the source

The preferred approach is to clone the Bitsquare repository using [git](http://www.git-scm.com/):

    git clone https://github.com/bitsquare/bitsquare.git

However, if you're not familiar with git or it is otherwise inconvenient to use, you can also download and extract a zip file of the latest sources at https://github.com/bitsquare/bitsquare/archive/master.zip.


### 2. Build

Bitsquare uses [Gradle](http://www.gradle.org/), and the [Gradle wrapper](http://www.gradle.org/docs/current/userguide/gradle_wrapper.html) as a build system. This means you don't need to download or do anything other than run the following command within the `bitsquare` directory.

    ./gradlew build

> _**NOTE:** on Windows, leave out the `./` and simply run `gradlew build`._


### 3. Run

When the build completes, you'll find executables and installers in the `build/distributions` directory.


Problems?
---------

If the instructions above don't work for you, please [raise an issue](https://github.com/bitsquare/bitsquare/issues/new?labels=%5Bbuild%5D). Thanks!
