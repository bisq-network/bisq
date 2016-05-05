<img src="https://bitsquare.io/images/logo.png" width="240"/>

[![Build Status](https://travis-ci.org/bitsquare/bitsquare.svg?branch=master)](https://travis-ci.org/bitsquare/bitsquare)


What is Bitsquare?
------------------

Bitsquare is a cross-platform desktop application that allows users to trade national currency (dollars, euros, etc) for bitcoin without relying on centralized exchanges such as Coinbase, Bitstamp or (the former) Mt. Gox.

By running Bitsquare on their local machines, users form a peer-to-peer network. Offers to buy and sell bitcoin are broadcast to that network, and through the process of offering and accepting these trades via the Bitsquare UI, a market is established.

There are no central points of control or failure in the Bitsquare network. There are no trusted third parties. When two parties agree to trade national currency for bitcoin, the bitcoin to be bought or sold is held in escrow using multisignature transaction capabilities native to the bitcoin protocol.

Because the national currency portion of any trade must be transferred via traditional means such as a wire transfer, Bitsquare incorporates first-class support for human arbitration to resolve any errors or disputes.

You can read about all of this and more in the [whitepaper](https://bitsquare.io/bitsquare.pdf), [arbitration](https://bitsquare.io/arbitration_system.pdf) and [risk analysis](https://bitsquare.io/risk_analysis.pdf) documents. Several [videos](https://bitsquare.io/blog/category/video) are available as well.

Status
------
Bitsquare has releases the beta version on the 27th of April 2016 after 3 months testing on mainnet.  
For the latest version checkout our [releases page](https://github.com/bitsquare/bitsquare/releases) at Github.

Building from source
--------------------

See [doc/build.md](doc/build.md).

[AUR for Arch Linux](https://aur.archlinux.org/packages/bitsquare-git)


Staying in Touch
----------------

Contact the team and keep up to date using any of the following:

 - The [Bitsquare Website](https://bitsquare.io)
 - The [#bitsquare](https://webchat.freenode.net/?channels=bitsquare) IRC channel on Freenode ([logs](https://botbot.me/freenode/bitsquare)) 
 - Our [mailing list](https://groups.google.com/forum/#!forum/bitsquare)
 - [@bitsquare_](https://twitter.com/bitsquare_) on Twitter
 - Get in [contact](https://bitsquare.io/contact/) with us
 - GitHub [Issues](https://github.com/bitsquare/bitsquare/issues)


License
-------

Bitsquare is [free software](https://www.gnu.org/philosophy/free-sw.html), licensed under version 3 of the [GNU Affero General Public License](https://gnu.org/licenses/agpl.html).

In short, this means you are free to fork this repository and do anything with it that you please. However, if you _distribute_ your changes, i.e. create your own build of the software and make it available for others to use, you must:

 1. Publish your changes under the same license, so as to ensure the software remains free.
 2. Use a name and logo substantially different than "Bitsquare" and the Bitsquare logo seen here. This allows for competition without confusion.

See [LICENSE](LICENSE) for complete details.
