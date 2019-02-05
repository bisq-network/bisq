# Bisq Network Monitor Node

The Bisq monitor node collects a set of metrics which are of interest to developers and users alike. These metrics are then made available through reporters.

The *Settled* release features these metrics:
- Tor Startup Time: The time it takes to start Tor starting at a clean system, unpacking the shipped Tor binaries, firing up Tor until Tor is connected to the Tor network and ready to use.
- Tor Roundtrip Time: Given a bootstrapped Tor, the roundtrip time of connecting to a hidden service is measured.
- Tor Hidden Service Startup Time: Given a bootstrapped Tor, the time it takes to create and announce a freshly created hidden service.
- P2P Round Trip Time: A metric hitchhiking the Ping/Pong messages of the Keep-Alive-Mechanism to determine the Round Trip Time when issuing a Ping to a seed node.
- P2P Seed Node Message Snapshot: Get absolute number and constellation of messages a fresh Bisq client will get on startup. Also reports diffs between seed nodes on a per-message-type basis.
- P2P Network Load: listens to the P2P network and its broadcast messages. Reports every X seconds.
- P2P Market Statistics: a demonstration metric which extracts market information from broadcast messages. This demo implementation reports the number of open offers per market .


The *Settled* release features these reporters:
- A reporter that simply writes the findings to `System.err`
- A reporter that reports the findings to a Graphite/Carbon instance using the [plaintext protocol](https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-plaintext-protocol)

## Configuration

The *Bisq Network Monitor Node* is to be configured via a Java properties file. The location of the file is to be passed as command line parameter:

```
./bisq-monitor /path/to/your/config.properties
```

A sample configuration file looks like follows:

```
## System configuration

# true overwrites the reporters picked by the developers (for debugging for example) (defaults to false)
System.useConsoleReporter=true

## Each Metric is configured via a set of properties.
##
## The minimal set of properties required to run a Metric is:
##
## YourMetricName.enabled=true|false
## YourMetricName.run.interval=10 [seconds]

#Edit and uncomment the lines below to your liking

#TorStartupTime Metric
TorStartupTime.enabled=true
TorStartupTime.run.interval=100
TorStartupTime.run.socksPort=90500 # so that there is no interference with a system Tor

#TorRoundTripTime Metric
TorRoundTripTime.enabled=true
TorRoundTripTime.run.interval=100
TorRoundTripTime.run.sampleSize=5
TorRoundTripTime.run.hosts=http://expyuzz4wqqyqhjn.onion:80 # torproject.org hidden service

#TorHiddenServiceStartupTime Metric
TorHiddenServiceStartupTime.enabled=true
TorHiddenServiceStartupTime.run.interval=100
TorHiddenServiceStartupTime.run.localPort=90501 # so that there is no interference with a system Tor
TorHiddenServiceStartupTime.run.servicePort=90511 # so that there is no interference with a system Tor

#P2PRoundTripTime Metric
P2PRoundTripTime.enabled=true
P2PRoundTripTime.run.interval=100
P2PRoundTripTime.run.sampleSize=5
P2PRoundTripTime.run.hosts=723ljisnynbtdohi.onion:8000, fl3mmribyxgrv63c.onion:8000
P2PRoundTripTime.run.torProxyPort=9060

#P2PNetworkLoad Metric
P2PNetworkLoad.enabled=true
P2PNetworkLoad.run.interval=100
P2PNetworkLoad.run.hosts=723ljisnynbtdohi.onion:8000, fl3mmribyxgrv63c.onion:8000
P2PNetworkLoad.run.torProxyPort=9061
P2PNetworkLoad.run.historySize=200

#P2PNetworkMessageSnapshot Metric
P2PSeedNodeSnapshot.enabled=true
P2PSeedNodeSnapshot.run.interval=24
P2PSeedNodeSnapshot.run.hosts=3f3cu2yw7u457ztq.onion:8000, 723ljisnynbtdohi.onion:8000, fl3mmribyxgrv63c.onion:8000
P2PSeedNodeSnapshot.run.torProxyPort=9062

#P2PMarketStats Metric
P2PMarketStats.enabled=true
P2PMarketStats.run.interval=37
P2PMarketStats.run.hosts=ef5qnzx6znifo3df.onion:8000
P2PMarketStats.run.torProxyPort=9063

## Reporters are configured via a set of properties as well.
##
## In contrast to Metrics, Reporters do not have a minimal set of properties.

#GraphiteReporter
GraphiteReporter.serviceUrl=k6evlhg44acpchtc.onion:2003

```
