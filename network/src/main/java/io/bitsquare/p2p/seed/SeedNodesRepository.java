package io.bitsquare.p2p.seed;

import com.google.common.collect.Sets;
import io.bitsquare.p2p.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

public class SeedNodesRepository {
    private static final Logger log = LoggerFactory.getLogger(SeedNodesRepository.class);

    // mainnet use port 8000
    // testnet use port 8001
    // regtest use port 8002
    private Set<Address> torSeedNodeAddresses = Sets.newHashSet(
            // mainnet
            new Address("lih5zsr2bvxi24pk.onion:8000"),
            new Address("s5xpstlooosehtxm.onion:8000"),
            new Address("izs5oz7i5ta7c2ir.onion:8000"),

            // testnet
            new Address("znmy44wcstn2rkva.onion:8001"),
            new Address("zvn7umikgxml6x6h.onion:8001"),
            new Address("wnfxmrmsyeeos2dy.onion:8001"),

            // regtest
            new Address("rxdkppp3vicnbgqt.onion:8002"),
            new Address("brmbf6mf67d2hlm4.onion:8002"),
            new Address("mfla72c4igh5ta2t.onion:8002")
    );


    private Set<Address> localhostSeedNodeAddresses = Sets.newHashSet(
            // mainnet
            new Address("localhost:2000"),
            new Address("localhost:3000"),
            new Address("localhost:4000"),

            // testnet
            new Address("localhost:2001"),
            new Address("localhost:3001"),
            new Address("localhost:4001"),

            // regtest
            new Address("localhost:2002"),
            new Address("localhost:3002"),
            new Address("localhost:4002")
    );

    public Set<Address> geSeedNodeAddresses(boolean useLocalhost, int networkId) {
        String networkIdAsString = String.valueOf(networkId);
        Set<Address> addresses = useLocalhost ? localhostSeedNodeAddresses : torSeedNodeAddresses;
        Set<Address> filtered = addresses.stream()
                .filter(e -> String.valueOf(e.port).endsWith(networkIdAsString)).collect(Collectors.toSet());
        log.info("SeedNodeAddresses (useLocalhost={}) for networkId {}:\nnetworkId={}", useLocalhost, networkId, filtered);
        return filtered;
    }

    public void setTorSeedNodeAddresses(Set<Address> torSeedNodeAddresses) {
        this.torSeedNodeAddresses = torSeedNodeAddresses;
    }

    public void setLocalhostSeedNodeAddresses(Set<Address> localhostSeedNodeAddresses) {
        this.localhostSeedNodeAddresses = localhostSeedNodeAddresses;
    }
}
