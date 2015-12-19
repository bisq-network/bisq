Building From Source
====================

This guide will walk you through the process of building Bitsquare from source.

> _**NOTE:** For most users, building from source is not necessary. See the [releases page](https://github.com/bitsquare/bitsquare/releases), where you'll find installers for Windows, Linux and Mac OS X._


For the impatient
-----------------

What follows is explained in detail in the sections below, but for those who know their way around Java, git and Maven, here are the instructions in a nutshell:

    $ javac -version
    javac 1.8.0_20       # must be 1.8.0_20 or better

    $ git clone https://github.com/bitsquare/bitsquare.git
    $ cd bitsquare
    $ mvn package    

When the build completes, you will find an executable jar: `gui/target/shaded.jar`. 
To run it use:
    $ java -jar gui/target/shaded.jar

To build the binary check out the build scripts under the package directory.

Prerequisites
-------------

The only prerequisite for building Bitsquare is installing the Java Development Kit (JDK), version 8u20 or better (as well as maven and git).

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


### 2. Build jar

Bitsquare uses maven as a build system. 

    $ cd bitsquare
    $ mvn package
    

### 3. Run

When the build completes, you will find an executable jar: `gui/target/shaded.jar`. 
To run it use:

    $ java -jar gui/target/shaded.jar
    
Please not that testnet is the default bitcoin network. Mainnet is disabled at that stage of development and will be activated as soon there is more testing done.
    
### 4. Development mode
  
Please check out our wiki for more information about [testing](https://github.com/bitsquare/bitsquare/wiki/Guide-for-testing-Bitsquare)
and how to use [regtest](https://github.com/bitsquare/bitsquare/wiki/How-to-use-Bitsquare-with-regtest-%28advanced%29)


Problems?
---------

If the instructions above don't work for you, please [raise an issue](https://github.com/bitsquare/bitsquare/issues/new?labels=%5Bbuild%5D). Thanks!
