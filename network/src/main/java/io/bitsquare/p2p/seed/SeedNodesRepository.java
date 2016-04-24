package io.bitsquare.p2p.seed;

import com.google.common.collect.Sets;
import io.bitsquare.p2p.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

public class SeedNodesRepository {
    private static final Logger log = LoggerFactory.getLogger(SeedNodesRepository.class);

    // mainnet use port 8000
    // testnet use port 8001
    // regtest use port 8002
    private Set<NodeAddress> torSeedNodeAddresses = Sets.newHashSet(
            // In alpha we change the network with new releases. That will be faded out once we become backwards compatible (Beta)

            // We keep 1 seed node running for the last 2 mainnet versions, just in case a user has not updated and need to 
            // access still his his app

            // mainnet
            // v0.3.5, v0.3.6 (backwards compatible)
            /*new NodeAddress("hulvbm5xjn7b7ku4.onion:8000"),
            new NodeAddress("3efgjjbdvhbvck3x.onion:8000"),
            new NodeAddress("3unfcshgwipxhxfm.onion:8000"),*/

            // v0.4.0, v0.4.1
           /* new NodeAddress("ybmi4iaesugslxrw.onion:8000"),
            new NodeAddress("ufwnvo775jfnjeux.onion:8000"),
            new NodeAddress("b66vnevaljo6xt5a.onion:8000"),*/

            // v0.4.2
            new NodeAddress("uadzuib66jupaept.onion:8000"),
            new NodeAddress("hbma455xxbqhcuqh.onion:8000"),
            new NodeAddress("wgthuiqn3aoiovbm.onion:8000"),
            new NodeAddress("2zxtnprnx5wqr7a3.onion:8000"),

            // testnet
            new NodeAddress("znmy44wcstn2rkva.onion:8001"),

            // regtest
            // For development you need to change that to your local onion addresses
            // 1. Run a seed node with prog args: rxdkppp3vicnbgqt.onion:8002 2 50
            // 2. Find your local onion address in Bitsquare_seed_node_rxdkppp3vicnbgqt.onion_8002/tor/hiddenservice/hostname
            // 3. Shut down the seed node
            // 4. Rename the directory with your local onion address    
            // 5. Edit here your found onion address (new NodeAddress("YOUR_ONION.onion:8002")
            new NodeAddress("rxdkppp3vicnbgqt.onion:8002"),
            new NodeAddress("brmbf6mf67d2hlm4.onion:8002"),
            new NodeAddress("mfla72c4igh5ta2t.onion:8002")
    );

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
            new NodeAddress("localhost:2002"),
            new NodeAddress("localhost:3002"),
            new NodeAddress("localhost:4002")
    );
    private NodeAddress nodeAddressToExclude;

    public Set<NodeAddress> getSeedNodeAddresses(boolean useLocalhost, int networkId) {
        String networkIdAsString = String.valueOf(networkId);
        Set<NodeAddress> nodeAddresses = useLocalhost ? localhostSeedNodeAddresses : torSeedNodeAddresses;
        Set<NodeAddress> filtered = nodeAddresses.stream()
                .filter(e -> String.valueOf(e.port).endsWith(networkIdAsString))
                .filter(e -> !e.equals(nodeAddressToExclude))
                .collect(Collectors.toSet());
        log.info("SeedNodeAddresses (useLocalhost={}) for networkId {}:\nnetworkId={}", useLocalhost, networkId, filtered);
        return filtered;
    }

    public void setTorSeedNodeAddresses(Set<NodeAddress> torSeedNodeAddresses) {
        this.torSeedNodeAddresses = torSeedNodeAddresses;
    }

    public void setLocalhostSeedNodeAddresses(Set<NodeAddress> localhostSeedNodeAddresses) {
        this.localhostSeedNodeAddresses = localhostSeedNodeAddresses;
    }

    public void setNodeAddressToExclude(NodeAddress nodeAddress) {
        this.nodeAddressToExclude = nodeAddress;
    }
}
