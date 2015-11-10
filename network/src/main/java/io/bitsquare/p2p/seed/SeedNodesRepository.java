package io.bitsquare.p2p.seed;

import com.google.common.collect.Sets;
import io.bitsquare.p2p.Address;

import java.util.Set;
import java.util.stream.Collectors;

public class SeedNodesRepository {

    // mainnet use port 8000
    // testnet use port 8001
    // regtest use port 8002
    private Set<Address> torSeedNodeAddresses = Sets.newHashSet(
            // mainnet
            new Address("lmvdenjkyvx2ovga.onion:8000"),
            new Address("eo5ay2lyzrfvx2nr.onion:8000"),
            new Address("si3uu56adkyqkldl.onion:8000"),

            // testnet
            new Address("lmvdenjkyvx2ovga.onion:8001"),
            new Address("eo5ay2lyzrfvx2nr.onion:8001"),
            new Address("si3uu56adkyqkldl.onion:8001"),

            // regtest
            new Address("lmvdenjkyvx2ovga.onion:8002"),
            new Address("eo5ay2lyzrfvx2nr.onion:8002"),
            new Address("si3uu56adkyqkldl.onion:8002")
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
        return addresses.stream()
                .filter(e -> String.valueOf(e.port).endsWith(networkIdAsString)).collect(Collectors.toSet());
    }

    public void setTorSeedNodeAddresses(Set<Address> torSeedNodeAddresses) {
        this.torSeedNodeAddresses = torSeedNodeAddresses;
    }

    public void setLocalhostSeedNodeAddresses(Set<Address> localhostSeedNodeAddresses) {
        this.localhostSeedNodeAddresses = localhostSeedNodeAddresses;
    }
}
