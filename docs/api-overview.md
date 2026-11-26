## API overview

Starting with v1.9.0 you can use pre-built versions of the Bisq cli (bisq-cli-v1.9.11.zip) and Bisq daemon (
bisq-daemon-v1.9.11.zip) to use Bisq without touching the user interface.

Just download the archives and extract them locally. You have to run the daemon to access the local Bisq daemon API
endpoints.

To run daemon.jar on Mainnet:

`$ java -jar daemon.jar --apiPassword=becareful`

The daemon does not start without `--apiPassword`. It also does not start before you accepted the terms and
conditions of Bisq: at the first start it prints them and shuts down. Read them, and if you accept them, start once
with

`$ java -jar daemon.jar --apiPassword=becareful --acceptTerms=true`

The daemon stores the acceptance, so later starts do not need the option. Do not keep the option in a start script,
or you also accept later versions of the terms without seeing them. The API listens on `127.0.0.1` only. `--apiHost` sets another
address, but the API has no TLS, so the password and all calls then cross the network in clear text. To use the API
from another host, prefer an SSH tunnel to the loopback address.

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
