# Bisq Network Monitor Node

The Bisq monitor node collects a set of metrics which are of interest to developers and users alike. These metrics are then made available through reporters.

The *Babysteps* release features these metrics:
- Tor Startup Time: The time it takes to start Tor starting at a clean system, unpacking the shipped Tor binaries, firing up Tor until Tor is connected to the Tor network and ready to use.
- Tor Roundtrip Time: Given a bootstrapped Tor, the roundtrip time of connecting to a hidden service is measured.
- Tor Hidden Service Startup Time: Given a bootstrapped Tor, the time it takes to create and announce a freshly created hidden service.

The *Babysteps* release features these reporters:
- A reporter that simply writes the findings to `System.err`
- A reporter that reports the findings to a Graphite/Carbon instance using the [plaintext protocol](https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-plaintext-protocol)

## Configuration

The *Bisq Network Monitor Node* is to be configured via a Java properties file. The location of the file is to be passed as command line parameter:

```
./bisq-monitor /path/to/your/config.properties
```

A sample configuration file looks like follows:

```
## Each Metric is configured via a set of properties.
##
## The minimal set of properties required to run a Metric is:
##
## YourMetricName.enabled=true|false
## YourMetricName.run.interval=10 [seconds]

#Edit and uncomment the lines below for your liking

#TorStartupTime Metric
TorStartupTime.enabled=true
TorStartupTime.run.interval=100
TorStartupTime.run.socksPort=90500 # so that there is no interference with a system Tor

#TorRoundtripTime Metric
TorRoundtripTime.enabled=true
TorRoundtripTime.run.interval=100
TorRoundtripTime.run.sampleSize=5
TorRoundtripTime.run.hosts=http://expyuzz4wqqyqhjn.onion:80 # torproject.org hidden service

#TorHiddenServiceStartupTime Metric
TorHiddenServiceStartupTime.enabled=true
TorHiddenServiceStartupTime.run.interval=100
TorHiddenServiceStartupTime.run.localPort=90501 # so that there is no interference with a system Tor
TorHiddenServiceStartupTime.run.servicePort=90511 # so that there is no interference with a system Tor

## Reporters are configured via a set of properties as well.
##
## In contrast to Metrics, Reporters do not have a minimal set of properties.

#GraphiteReporter
GraphiteReporter.serviceUrl=http://yourHiddenService.onion:2003
```