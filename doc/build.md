Building From Source
====================

This guide will walk you through the process of building Bitsquare from source.

> _**NOTE:** For most users, building from source is not necessary. See the [releases page](https://github.com/bitsquare/bitsquare/releases), where you'll find installers for Windows, Linux and Mac OS X._

There is an install script (2 parts) for setup (JDK, git, maven, Bitcoinj, Bitsquare) on Linux in that directory (install_on_unix.sh, install_on_unix_fin.sh).

System requirements
-------------

The prerequisite for building Bitsquare is installing the Java Development Kit (JDK), version 8u66 or better (as well as maven and git).
In Debian/Ubuntu systems with OpenJDK you'll need OpenJFX as well, i.e. you'll need the `openjfx` package besides the `openjdk-8-jdk` package.

### 1. Check the version of Java you currently have installed

    $ java -version

If `java` is not found, or your version is anything less than `1.8.0_66`, then follow the next steps, otherwise you can skip to step 2:

#### 1.1 Debian based systems (Ubuntu)

To install OpenJDK use:

    $ sudo apt-get install openjdk-8-jdk maven libopenjfx-java

To install the Oracle JDK use:
 
    $ sudo add-apt-repository ppa:webupd8team/java
    $ sudo apt-get update
    $ sudo apt-get -y install oracle-java8-installer

Check if $JAVA_HOME is set

    $ echo $JAVA_HOME
    
If $JAVA_HOME is not present, add it to the .bashrc file

    $ touch .bashrc
    $ gedit .bashrc
    $ export JAVA_HOME=/usr/lib/jvm/java-8-oracle
    $ echo $JAVA_HOME 

#### 1.2 Other systems

[Download and install the latest JDK]( http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) for your platform.

Build bitcoinj
-----------------
### 2. Install bitcoinj fork 
> _**NOTE:** 
Bitcoinj versions later than 0.13.1 has removed support for Java serialisation. 
In version 0.13.1 is also missing support for Java serialisation in MainNetParams (HttpDiscovery.Details).
We removed usage of Cartographer/HttpDiscovery and fixed privacy issues with Bloom Filters at our [fork version 0.13.1.5](https://github.com/bitsquare/bitcoinj/tree/FixBloomFilters).
Beside the Java serialisation issues there are [privacy concerns](http://bitcoin-development.narkive.com/hczWIAby/bitcoin-development-cartographer#post3) regarding Cartographer. 
Here is a Github issue with background and open tasks regarding [Bloom Filters](https://github.com/bitsquare/bitsquare/issues/414)._
                
    $ git clone -b FixBloomFilters https://github.com/bitsquare/bitcoinj.git               
    $ cd bitcoinj  
    $ mvn clean install -DskipTests -Dmaven.javadoc.skip=true

Prepare Bitsquare build
-----------------

### 3. Get Bitsquare source code and build a preliminary Bitsquare version

You need to get the Bitsquare dependencies first as we need to copy the BountyCastle jar to the JRE directory as well as the jdkfix jar. 

    $ git clone https://github.com/bitsquare/bitsquare.git
    $ cd bitsquare
    $ mvn clean package -DskipTests -Dmaven.javadoc.skip=true
      
### 4. Copy the jdkfix jar file
      
Copy the jdkfix-0.4.9.5.jar from the Bitsquare jdkfix/target directory to $JAVA_HOME/jre/lib/ext/. 
jdkfix-0.4.9.5.jar includes a bugfix of the SortedList class which will be released with the next JDK version. 
We need to load that class before the default java class. This step will be removed once the bugfix is in the official JDK.
    
    $ sudo cp bitsquare/jdkfix/target/jdkfix-0.4.9.5.jar $JAVA_HOME/jre/lib/ext/jdkfix-0.4.9.5.jar

### 5. Copy the BountyCastle provider jar file

Copy the BountyCastle provider jar file from the local maven repository to the jre/lib/ext directory.
This prevents a "JCE cannot authenticate the provider BC" exception when starting the Bitsquare client.
    
    $ sudo cp ~/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.53/bcprov-jdk15on-1.53.jar $JAVA_HOME/jre/lib/ext/bcprov-jdk15on-1.53.jar

### 6. Edit the java.security file and add BouncyCastleProvider

Add org.bouncycastle.jce.provider.BouncyCastleProvider as last entry at: ﻿List of providers and their preference orders
E.g.:
security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider
    
    $ sudo gedit $JAVA_HOME/jre/lib/security/java.security
    ... edit and save
  
### 7. Enable unlimited Strength for cryptographic keys (if Oracle JDK is used)

If you are using Oracle JDK you need to follow the following step. If you use OpenJDK + OpenJFX you can skip that step.
Bitsquare uses 256 bit length keys which are still not permitted by default. 
Get around that ridiculous fact by adding the missing [jars from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html). 
Please follow the steps described in the Readme file at the downloaded package.
You will get an error when building Bitsquare package if you don't have these.
   
    $ wget --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jce/8/jce_policy-8.zip
    $ unzip jce_policy-8.zip
    $ sudo cp UnlimitedJCEPolicyJDK8/US_export_policy.jar $JAVA_HOME/jre/lib/security/US_export_policy.jar
    $ sudo cp UnlimitedJCEPolicyJDK8/local_policy.jar $JAVA_HOME/jre/lib/security/local_policy.jar
    $ sudo chmod 777 /usr/lib/jvm/java-8-oracle/jre/lib/security/US_export_policy.jar
    $ sudo chmod 777 /usr/lib/jvm/java-8-oracle/jre/lib/security/local_policy.jar
    $ sudo rm -r UnlimitedJCEPolicyJDK8
    $ sudo rm jce_policy-8.zip

Build Bitsquare
-----------------

### 8. Build final Bitsquare jar

Now we have all prepared to build the correct Bitsquare jar. 
    
    $ mvn clean package -DskipTests -Dmaven.javadoc.skip=true
    
When the build completes, you will find an executable jar: `gui/target/shaded.jar`. 
To run it use:

    $ java -jar gui/target/shaded.jar

Build binaries
-----------------

If you want to build the binaryies check out the build scripts under the package directory.

Development mode
-----------------
  
Please check out our wiki for more information about [testing](https://github.com/bitsquare/bitsquare/wiki/Testing-Bitsquare-with-Mainnet)
and how to use [regtest](https://github.com/bitsquare/bitsquare/wiki/How-to-use-Bitsquare-with-regtest-%28advanced%29)

Here are example program arguments for using regtest with localhost environment (not using Tor):  
    
    $ java -jar seednode/target/SeedNode.jar --bitcoinNetwork=REGTEST --useLocalhost=true --myAddress=localhost:2002 --nodePort=2002 --appName=Bitsquare_seed_node_localhost_2002 
   
    $ java -jar gui/target/shaded.jar --bitcoinNetwork=REGTEST --useLocalhost=true --myAddress=localhost:2222 --nodePort=2222 --appName=Bitsquare-Local-Regtest-Arbitrator  
    
    $ java -jar gui/target/shaded.jar --bitcoinNetwork=REGTEST --useLocalhost=true --myAddress=localhost:3333 --nodePort=3333 --appName=Bitsquare-Local-Regtest-Alice 
   
    $ java -jar gui/target/shaded.jar --bitcoinNetwork=REGTEST --useLocalhost=true --myAddress=localhost:4444 --nodePort=4444 --appName=Bitsquare-Local-Regtest-Bob
  
  
Running local seed node with Tor and RegTest
-----------------

If you want to run locally a seed node via Tor you need to add your seed node's hidden service address to the SeedNodesRepository.java class.
You can find the hidden service address after you started once a seed node. Start it with a placeholder address like: 
   
    $ java -jar seednode/target/SeedNode.jar --bitcoinNetwork=REGTEST --nodePort=8002 --myAddress=xxxxxxxx.onion:8002 --appName=Bitsquare_seed_node_xxxxxxxx.onion_8000
    
Once the hidden service is published (check console output) quit the seed node and copy the hidden service address from the console output. 
Alternatively you can navigate to the application directory and open Bitsquare_seed_node_xxxxxxx.onion_8002/tor/hiddenservice/hostname.
use that hidden service address also to rename the xxxxxxx placeholder of your Bitsquare_seed_node_xxxxxxx.onion_8002 directory.
Start again the SeedNode.jar now with the correct hidden service address.
Instructions are also at the SeedNodesRepository class.
              
Here are example program arguments for using regtest and using the Tor network (example onion address is ewdkppp3vicnbgqt):  
    
     $ java -jar seednode/target/SeedNode.jar ewdkppp3vicnbgqt.onion:8002 2 50  
   
     $ java -jar seednode/target/SeedNode.jar --bitcoinNetwork=REGTEST --nodePort=8002 --myAddress=ewdkppp3vicnbgqt.onion:8002 --appName=Bitsquare_seed_node_ewdkppp3vicnbgqt.oinion_8002 
      
     $ java -jar gui/target/shaded.jar --bitcoinNetwork=REGTEST --myAddress=localhost:2222 --nodePort=2222 --appName=Bitsquare-Local-Regtest-Arbitrator  
       
     $ java -jar gui/target/shaded.jar --bitcoinNetwork=REGTEST --myAddress=localhost:3333 --nodePort=3333 --appName=Bitsquare-Local-Regtest-Alice 
      
     $ java -jar gui/target/shaded.jar --bitcoinNetwork=REGTEST --myAddress=localhost:4444 --nodePort=4444 --appName=Bitsquare-Local-Regtest-Bob
     
   
Problems?
---------

If the instructions above don't work for you, please [raise an issue](https://github.com/bitsquare/bitsquare/issues/new?labels=%5Bbuild%5D). Thanks!
