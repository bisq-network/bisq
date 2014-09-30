![Bitsquare Logo](http://bitsquare.io/images/logo_240.png)

[![Build Status](https://travis-ci.org/bitsquare/bitsquare.svg?branch=master)](https://travis-ci.org/bitsquare/bitsquare)

## About
Bitsquare is a **P2P Fiat-BTC Exchange**.   
It allows to trade fiat money (USD, EURO, ...) for Bitcoins without relying on a centralized exchange like Coinbase or BitStamp.  
Instead, all participants form a peer to peer market.

## Dependencies

 - [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

For installing Java 8 on Linux user that:  
sudo apt-get purge openjdk*  
sudo add-apt-repository -y ppa:webupd8team/java  
sudo apt-get update  
sudo apt-get install oracle-java8-installer  

## Development setup

    git clone https://github.com/bitsquare/bitsquare.git
    ./gradlew build

### Regtest mode for local testing  
For local testing it is best to use the **regtest** mode from the Bitcoin QT client.  
You need to edit (or first create inside the bitcoin data directory) the bitcoin.config file and set regtest=1.  

Here are the typical locations for the data directory:

Windows:  
%APPDATA%\Bitcoin\  
(XP) C:\Documents and Settings\username\Application Data\Bitcoin\bitcoin.conf  
(Vista, 7) C:\Users\username\AppData\Roaming\Bitcoin\bitcoin.conf  

Linux:  
$HOME/.bitcoin/  
/home/username/.bitcoin/bitcoin.conf  

Mac OSX:  
$HOME/Library/Application Support/Bitcoin/  
/Users/username/Library/Application Support/Bitcoin/bitcoin.conf  

Take care if you have real bitcoins in your Bitcoin QT wallet (backup and copy first your data directory)!  
More information about bitcoin.conf can be found [here](https://en.bitcoin.it/wiki/Running_Bitcoin).

You can generate coins on demand with the Bitcoin QT client with the following command in the console (under the help menu you find the console window):  
**setgenerate true 101**  
101 is used only for the first start because of the coin maturity of 100 blocks. Later for mining of a single block you can use 1 as number of blocks to be created.

More information about the regtest mode can be found [here](https://bitcoinj.github.io/testing) or [here](https://bitcoin.org/en/developer-examples#regtest-mode).  

The network mode is defined in the guice module (BitSquareModule) and is default set to regtest.  
Testnet should also work, but was not tested for a while as for developing regtest is much more convenient.  
Please don't use main net with real money, as the software is under heavy development and you can easily lose your funds!


## Resources:
* [Web](http://bitsquare.io)
* [Overview](http://bitsquare.io/images/overview.png)
* [Videos](https://www.youtube.com/playlist?list=PLXvC3iNe_di9bL1A5xyAKI2PzNg8jU092)
* [White paper](https://docs.google.com/document/d/1d3EiWZdaM89-P6MVhS53unXv2-pDpSFsN3W4kCGXKgY/edit)


## Communication channels:
* [Mailing list](https://groups.google.com/forum/#!forum/bitsquare)
* [IRC](https://webchat.freenode.net/?channels=bitsquare)
* [Bitcoin forum](https://bitcointalk.org/index.php?topic=647457)
* [Twitter](https://twitter.com/bitsquare_)
* [Email](mailto:team@bitsquare.io)
