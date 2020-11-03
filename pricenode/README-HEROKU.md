Deploy on Heroku
--------

Run the following commands:

    heroku create
    heroku buildpacks:add heroku/gradle
    git push heroku master
    curl https://your-app-123456.herokuapp.com/getAllMarketPrices

To register the node as a Tor hidden service, first install the Heroku Tor buildpack:

    heroku buildpacks:add https://github.com/cbeams/heroku-buildpack-tor.git
    git push heroku master

> NOTE: this deployment will take a while, because the new buildpack must download and build Tor from source.

Next, generate your Tor hidden service private key and .onion address:

    heroku run bash
    ./tor/bin/tor -f torrc

When the process reports that it is "100% bootstrapped", kill it, then copy the generated private key and .onion hostname values:

    cat build/tor-hidden-service/hostname
    cat build/tor-hidden-service/private_key
    exit

> IMPORTANT: Save the private key value in a secure location so that this node can be re-created elsewhere with the same .onion address in the future if necessary.

Now configure the hostname and private key values as environment variables for your Heroku app:

    heroku config:set HIDDEN=true HIDDEN_DOT_ONION=[your .onion] HIDDEN_PRIVATE_KEY="[your tor privkey]"
    git push heroku master

When the application finishes restarting, you should still be able to access it via the clearnet, e.g. with:

    curl https://your-app-123456.herokuapp.com/getAllMarketPrices

And via your Tor Browser at:

    http://$YOUR_ONION/getAllMarketPrices
