## API overview

Starting with v1.9.0 you can use pre-built versions of the Bisq cli (bisq-cli-v1.9.11.zip) and Bisq daemon (
bisq-daemon-v1.9.11.zip) to use Bisq without touching the user interface.

Just download the archives and extract them locally. You have to run the daemon to access the local Bisq daemon API
endpoints.

To run daemon.jar on Mainnet:

`$ java -jar daemon.jar --apiPassword=becareful`

If you just want to control your headless daemon within your terminal you have to run the Bisq cli as well.
Again just download the bisq-cli archive and extract it locally.
To call getversion from cli.jar

`$ java -jar cli.jar --password=becareful getversion`

You can use the Bisq API to access local Bisq daemon API endpoints, which provide a subset of the Bisq Desktop
application's feature set: check balances, transfer BTC and BSQ, create payment accounts, view offers, create and take
offers, and execute trades.

The Bisq API is based on the gRPC framework, and any supported gRPC language binding can be used to call Bisq API
endpoints.

You'll find in-depth documentation and examples under following link: https://bisq-network.github.io/slate/#introduction

Bisq gRPC API reference documentation example source code is hosted on GitHub
at https://github.com/bisq-network/bisq-api-reference. Java and Python developers interested in bot development may find
this Intellij project useful for running the existing examples, and writing their own bots.

For additional developer support please join Development - Bisq v1 on Matrix.
