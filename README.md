<img src="https://bisq.network/images/logo_white_bg.png" width="410" />


What is Bisq?
------------------

Bisq is a cross-platform desktop application that allows users to trade national currency (dollars, euros, etc) for bitcoin without relying on centralized exchanges such as Coinbase, Bitstamp or (the former) Mt. Gox.

By running Bisq on their local machines, users form a peer-to-peer network. Offers to buy and sell bitcoin are broadcast to that network, and through the process of offering and accepting these trades via the Bisq UI, a market is established.

There are no central points of control or failure in the Bisq network. There are no trusted third parties. When two parties agree to trade national currency for bitcoin, the bitcoin to be bought or sold is held in escrow using multisignature transaction capabilities native to the bitcoin protocol.

Because the national currency portion of any trade must be transferred via traditional means such as a wire transfer, Bisq incorporates first-class support for human arbitration to resolve any errors or disputes.

You can read about all of this and more in the [whitepaper](https://bisq.network/bitsquare.pdf) and [arbitration](https://bisq.network/arbitration_system.pdf) documents. Several [videos](https://bisq.network/blog/category/video) are available as well.

Status
------
Bisq has released the beta version on the 27th of April 2016. It is operational since that time without any major incident.
Please follow the current development state at our [road map]( https://bisq.network/roadmap).
For the latest version checkout our [releases page](https://github.com/bisq-network/exchange/releases) at Github.

Building from source
--------------------

See [doc/build.md](doc/build.md).

[AUR for Arch Linux](https://aur.archlinux.org/packages/bisq-git/)


Staying in Touch
----------------

Contact the team and keep up to date using any of the following:

 - The [Bisq Website](https://bisq.network)
 - GitHub [Issues](https://github.com/bisq-network/exchange/issues)
 - The [Bisq Forum]( https://forum.bisq.network)
 - The [#bitsquare](https://webchat.freenode.net/?channels=bitsquare) IRC channel on Freenode ([logs](https://botbot.me/freenode/bitsquare))
 - Our [mailing list](https://groups.google.com/forum/#!forum/bitsquare)
 - [@Bitsquare_](https://twitter.com/bitsquare_) on Twitter


License
-------

Bisq is [free software](https://www.gnu.org/philosophy/free-sw.html), licensed under version 3 of the [GNU Affero General Public License](https://gnu.org/licenses/agpl.html).

In short, this means you are free to fork this repository and do anything with it that you please. However, if you _distribute_ your changes, i.e. create your own build of the software and make it available for others to use, you must:

 1. Publish your changes under the same license, so as to ensure the software remains free.
 2. Use a name and logo substantially different than "Bisq" and the Bisq logo seen here. This allows for competition without confusion.

See [LICENSE](LICENSE) for complete details.
