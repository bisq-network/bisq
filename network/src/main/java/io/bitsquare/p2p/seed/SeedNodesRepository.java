package io.bitsquare.p2p.seed;

import io.bitsquare.p2p.Address;

import java.util.Arrays;
import java.util.List;

public class SeedNodesRepository {


    protected List<Address> torSeedNodeAddresses = Arrays.asList(
            new Address("3anjm5mw2sr6abx6.onion:8001")
    );


    protected List<Address> localhostSeedNodeAddresses = Arrays.asList(
            new Address("localhost:8001"),
            new Address("localhost:8002"),
            new Address("localhost:8003")
    );

    public List<Address> getTorSeedNodeAddresses() {
        return torSeedNodeAddresses;
    }

    public List<Address> geSeedNodeAddresses(boolean useLocalhost) {
        return useLocalhost ? localhostSeedNodeAddresses : torSeedNodeAddresses;
    }

    public List<Address> getLocalhostSeedNodeAddresses() {
        return localhostSeedNodeAddresses;
    }

    public void setTorSeedNodeAddresses(List<Address> torSeedNodeAddresses) {
        this.torSeedNodeAddresses = torSeedNodeAddresses;
    }

    public void setLocalhostSeedNodeAddresses(List<Address> localhostSeedNodeAddresses) {
        this.localhostSeedNodeAddresses = localhostSeedNodeAddresses;
    }
}
