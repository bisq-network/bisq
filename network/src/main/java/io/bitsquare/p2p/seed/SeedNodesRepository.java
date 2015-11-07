package io.bitsquare.p2p.seed;

import com.google.common.collect.Sets;
import io.bitsquare.p2p.Address;

import java.util.Set;

public class SeedNodesRepository {


    private Set<Address> torSeedNodeAddresses = Sets.newHashSet(
            new Address("lmvdenjkyvx2ovga.onion:8001"),
            new Address("eo5ay2lyzrfvx2nr.onion:8002"),
            new Address("si3uu56adkyqkldl.onion:8003")
    );


    private Set<Address> localhostSeedNodeAddresses = Sets.newHashSet(
            new Address("localhost:8001"),
            new Address("localhost:8002"),
            new Address("localhost:8003")
    );

    public Set<Address> getTorSeedNodeAddresses() {
        return torSeedNodeAddresses;
    }

    public Set<Address> geSeedNodeAddresses(boolean useLocalhost) {
        return useLocalhost ? localhostSeedNodeAddresses : torSeedNodeAddresses;
    }

    public Set<Address> getLocalhostSeedNodeAddresses() {
        return localhostSeedNodeAddresses;
    }

    public void setTorSeedNodeAddresses(Set<Address> torSeedNodeAddresses) {
        this.torSeedNodeAddresses = torSeedNodeAddresses;
    }

    public void setLocalhostSeedNodeAddresses(Set<Address> localhostSeedNodeAddresses) {
        this.localhostSeedNodeAddresses = localhostSeedNodeAddresses;
    }
}
