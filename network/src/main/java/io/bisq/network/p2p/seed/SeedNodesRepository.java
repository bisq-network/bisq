package io.bisq.network.p2p.seed;

import com.google.common.collect.Sets;
import io.bisq.network.p2p.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SeedNodesRepository {
    private static final Logger log = LoggerFactory.getLogger(SeedNodesRepository.class);

    // Addresses are used if their port match the network id:
    // - mainnet uses port 8000
    // - testnet uses port 8001
    // - regtest uses port 8002
    @SuppressWarnings("ConstantConditions")
    private Set<NodeAddress> torSeedNodeAddresses = Sets.newHashSet(
            // BTC mainnet
            new NodeAddress("3f3cu2yw7u457ztq.onion:8000"),
            new NodeAddress("723ljisnynbtdohi.onion:8000"),
            new NodeAddress("rm7b56wbrcczpjvl.onion:8000"),
            new NodeAddress("fl3mmribyxgrv63c.onion:8000"),

            // local dev
            // new NodeAddress("ren4cuzpex24rdwo.onion:8000"),

            // BTC testnet
            new NodeAddress("nbphlanpgbei4okt.onion:8001"),

            // BTC regtest
            // For development you need to change that to your local onion addresses
            // 1. Run a seed node with prog args: --bitcoinNetwork=regtest --nodePort=8002 --myAddress=rxdkppp3vicnbgqt:8002 --appName=bisq_seed_node_rxdkppp3vicnbgqt.onion_8002
            // 2. Find your local onion address in bisq_seed_node_rxdkppp3vicnbgqt.onion_8002/regtest/tor/hiddenservice/hostname
            // 3. Shut down the seed node
            // 4. Rename the directory with your local onion address
            // 5. Edit here your found onion address (new NodeAddress("YOUR_ONION.onion:8002")
            new NodeAddress("rxdkppp3vicnbgqt.onion:8002"),
           /* new NodeAddress("brmbf6mf67d2hlm4.onion:8002"),
            new NodeAddress("mfla72c4igh5ta2t.onion:8002"),*/

            // LTC mainnet
            new NodeAddress("acyvotgewx46pebw.onion:8003"),
            new NodeAddress("bolqw3hs55uii7ku.onion:8003"),
            new NodeAddress("pklgy3vdfn3obkur.onion:8003"),
            new NodeAddress("cfciqxcowuhjdnkl.onion:8003"),

            // local dev
            // new NodeAddress("vlzlf3vs6yisxl4a.onion:8003"),

            // DOGE mainnet
            new NodeAddress("t6bwuj75mvxswavs.onion:8006")

            // local dev
            //new NodeAddress("iouuvpjnqjw4t3mp.onion:8006"),
            //new NodeAddress("6kdjei7twxj45j43.onion:8006")
    );

    // Addresses are used if the last digit of their port match the network id:
    // - mainnet use port ends in 0
    // - testnet use port ends in 1
    // - regtest use port ends in 2
    private Set<NodeAddress> localhostSeedNodeAddresses = Sets.newHashSet(
            // BTC
            // mainnet
            new NodeAddress("localhost:2000"),
            new NodeAddress("localhost:3000"),
            new NodeAddress("localhost:4000"),

            // testnet
            new NodeAddress("localhost:2001"),
            new NodeAddress("localhost:3001"),
            new NodeAddress("localhost:4001"),

            // regtest
            new NodeAddress("localhost:2002"),
            new NodeAddress("localhost:3002"),
            new NodeAddress("localhost:4002"),

            // LTC
            // mainnet
            new NodeAddress("localhost:2003"),

            // regtest
            new NodeAddress("localhost:2005"),

            // DOGE regtest
            new NodeAddress("localhost:2008")
    );
    private NodeAddress nodeAddressToExclude;

    public Set<NodeAddress> getSeedNodeAddresses(boolean useLocalhostForP2P, int networkId) {
        String networkIdAsString = String.valueOf(networkId);
        Set<NodeAddress> nodeAddresses = useLocalhostForP2P ? localhostSeedNodeAddresses : torSeedNodeAddresses;
        Set<NodeAddress> filtered = nodeAddresses.stream()
                .filter(e -> String.valueOf(e.getPort()).endsWith(networkIdAsString))
                .filter(e -> !e.equals(nodeAddressToExclude))
                .collect(Collectors.toSet());
        log.debug("SeedNodeAddresses (useLocalhostForP2P={}) for networkId {}:\nnetworkId={}",
                useLocalhostForP2P, networkId, filtered);
        return filtered;
    }

    public void setTorSeedNodeAddresses(Set<NodeAddress> torSeedNodeAddresses) {
        this.torSeedNodeAddresses = torSeedNodeAddresses;
    }

    public void setLocalhostSeedNodeAddresses(Set<NodeAddress> localhostSeedNodeAddresses) {
        this.localhostSeedNodeAddresses = localhostSeedNodeAddresses;
    }

    public boolean isSeedNode(NodeAddress nodeAddress) {
        return Stream.concat(localhostSeedNodeAddresses.stream(), torSeedNodeAddresses.stream())
                .filter(e -> e.equals(nodeAddress)).findAny().isPresent();
    }

    public void setNodeAddressToExclude(NodeAddress nodeAddress) {
        this.nodeAddressToExclude = nodeAddress;
    }
}
