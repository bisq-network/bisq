package io.bisq.network.p2p.seed;

import com.google.common.collect.Sets;
import io.bisq.common.app.DevEnv;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
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
    private Set<NodeAddress> torSeedNodeAddresses = Sets.newHashSet(
            // v0.5.0
            DevEnv.STRESS_TEST_MODE ? new NodeAddress("hlitt7z4bec4kdh4.onion:8000") : new NodeAddress("ren4cuzpex24rdwo.onion:8000"),

            // local dev test
            // DevFlags.STRESS_TEST_MODE ? new NodeAddress("hlitt7z4bec4kdh4.onion:8000") : new NodeAddress("23bnormzh2mvkz3z.onion:8000"),

            // testnet (not operated by bisq devs)
            new NodeAddress("znmy44wcstn2rkva.onion:8001"),

            // regtest
            // For development you need to change that to your local onion addresses
            // 1. Run a seed node with prog args: --bitcoinNetwork=regtest --nodePort=8002 --myAddress=rxdkppp3vicnbgqt:8002 --appName=bisq_seed_node_rxdkppp3vicnbgqt.onion_8002
            // 2. Find your local onion address in bisq_seed_node_rxdkppp3vicnbgqt.onion_8002/regtest/tor/hiddenservice/hostname
            // 3. Shut down the seed node
            // 4. Rename the directory with your local onion address
            // 5. Edit here your found onion address (new NodeAddress("YOUR_ONION.onion:8002")
            DevEnv.STRESS_TEST_MODE ? new NodeAddress("hlitt7z4bec4kdh4.onion:8002") : new NodeAddress("rxdkppp3vicnbgqt.onion:8002"),
            DevEnv.STRESS_TEST_MODE ? new NodeAddress("hlitt7z4bec4kdh4.onion:8002") : new NodeAddress("brmbf6mf67d2hlm4.onion:8002"),
            DevEnv.STRESS_TEST_MODE ? new NodeAddress("hlitt7z4bec4kdh4.onion:8002") : new NodeAddress("mfla72c4igh5ta2t.onion:8002")
    );

    // Addresses are used if the last digit of their port match the network id:
    // - mainnet use port ends in 0
    // - testnet use port ends in 1
    // - regtest use port ends in 2
    private Set<NodeAddress> localhostSeedNodeAddresses = Sets.newHashSet(
            // mainnet
            new NodeAddress("localhost:2000"),
            new NodeAddress("localhost:3000"),
            new NodeAddress("localhost:4000"),

            // testnet
            new NodeAddress("localhost:2001"),
            new NodeAddress("localhost:3001"),
            new NodeAddress("localhost:4001"),

            // regtest
            new NodeAddress("localhost:2002")
           /* new NodeAddress("localhost:3002"),
            new NodeAddress("localhost:4002")*/
    );
    private NodeAddress nodeAddressToExclude;

    public Set<NodeAddress> getSeedNodeAddresses(boolean useLocalhostForP2P, int networkId) {
        String networkIdAsString = String.valueOf(networkId);
        Set<NodeAddress> nodeAddresses = useLocalhostForP2P ? localhostSeedNodeAddresses : torSeedNodeAddresses;
        Set<NodeAddress> filtered = nodeAddresses.stream()
                .filter(e -> String.valueOf(e.port).endsWith(networkIdAsString))
                .filter(e -> !e.equals(nodeAddressToExclude))
                .collect(Collectors.toSet());
        log.debug("SeedNodeAddresses (useLocalhostForP2P={}) for networkId {}:\nnetworkId={}", useLocalhostForP2P, networkId, filtered);
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
