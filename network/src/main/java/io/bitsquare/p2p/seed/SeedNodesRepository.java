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
    private Set<NodeAddress> torSeedNodeNodeAddresses = Sets.newHashSet(
            // mainnet
            new NodeAddress("lih5zsr2bvxi24pk.onion:8000"),
            new NodeAddress("s5xpstlooosehtxm.onion:8000"),
            new NodeAddress("izs5oz7i5ta7c2ir.onion:8000"),

            // testnet
            new NodeAddress("znmy44wcstn2rkva.onion:8001"),
            new NodeAddress("zvn7umikgxml6x6h.onion:8001"),
            new NodeAddress("wnfxmrmsyeeos2dy.onion:8001"),

            // regtest
            new NodeAddress("rxdkppp3vicnbgqt.onion:8002"),
            new NodeAddress("brmbf6mf67d2hlm4.onion:8002"),
            new NodeAddress("mfla72c4igh5ta2t.onion:8002")
    );


    private Set<NodeAddress> localhostSeedNodeNodeAddresses = Sets.newHashSet(
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

    public Set<NodeAddress> getSeedNodeAddresses(boolean useLocalhost, int networkId) {
        String networkIdAsString = String.valueOf(networkId);
        Set<NodeAddress> nodeAddresses = useLocalhost ? localhostSeedNodeNodeAddresses : torSeedNodeNodeAddresses;
        Set<NodeAddress> filtered = nodeAddresses.stream()
                .filter(e -> String.valueOf(e.port).endsWith(networkIdAsString)).collect(Collectors.toSet());
        log.info("SeedNodeAddresses (useLocalhost={}) for networkId {}:\nnetworkId={}", useLocalhost, networkId, filtered);
        return filtered;
    }

    public void setTorSeedNodeNodeAddresses(Set<NodeAddress> torSeedNodeNodeAddresses) {
        this.torSeedNodeNodeAddresses = torSeedNodeNodeAddresses;
    }

    public void setLocalhostSeedNodeNodeAddresses(Set<NodeAddress> localhostSeedNodeNodeAddresses) {
        this.localhostSeedNodeNodeAddresses = localhostSeedNodeNodeAddresses;
    }
}
