Needed information to start a pricenode
==

Copy to this directory:
--

* a tor `hostname` file, containing your onion address
* a tor `private_key` file, containing the private key for your tor hidden service

Edit docker-compose.yml:
--

* fill in your public and private api keys (needs a btcaverage developer subscription)

Needed software to start a pricenode
==

* docker
* docker-compose

How to start
==

`docker-compose up -d`


How to monitor
==

See if it's running: `docker ps`

Check the logs: `docker-compose logs`


Notes when using CoreOs
==

Using CoreOs as host OS is entirely optional!

* the cloudconfig.yml file is a configuration file for starting a coreos machine
from scratch.
* when installing a Coreos server, docker-compose needs to be additionally installed next to the
already provided docker installation
