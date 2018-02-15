package io.bisq.core.network;

import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.seed.SeedNodesRepository;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import java.util.Set;
import java.util.stream.Stream;

import static io.bisq.core.network.DefaultNodeAddresses.DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES;
import static io.bisq.core.network.DefaultNodeAddresses.DEFAULT_TOR_SEED_NODE_ADDRESSES;

@NotThreadSafe
public class CoreSeedNodesRepository implements SeedNodesRepository {
    private final NodeAddressLookup lookup;

    private Set<NodeAddress> seedNodeAddresses;
    private Set<NodeAddress> torSeedNodeAddresses;
    private Set<NodeAddress> localhostSeedNodeAddresses;

    @Inject
    public CoreSeedNodesRepository(NodeAddressLookup lookup) {
        this.lookup = lookup;
        this.torSeedNodeAddresses = DEFAULT_TOR_SEED_NODE_ADDRESSES;
        this.localhostSeedNodeAddresses = DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES;
    }

    @Override
    public Set<NodeAddress> getSeedNodeAddresses() {
        if (seedNodeAddresses == null) {
            seedNodeAddresses = lookup.resolveNodeAddresses();
        }
        return seedNodeAddresses;
    }

    public String getOperator(NodeAddress nodeAddress) {
        switch (nodeAddress.getFullAddress()) {
            case "5quyxpxheyvzmb2d.onion:8000":
                return "@miker";
            case "ef5qnzx6znifo3df.onion:8000":
                return "@manfredkarrer";
            case "s67qglwhkgkyvr74.onion:8000":
                return "@emzy";
            case "jhgcy2won7xnslrb.onion:8000":
                return "@manfredkarrer";
            case "3f3cu2yw7u457ztq.onion:8000":
                return "@manfredkarrer";
            case "723ljisnynbtdohi.onion:8000":
                return "@manfredkarrer";
            case "rm7b56wbrcczpjvl.onion:8000":
                return "@manfredkarrer";
            case "fl3mmribyxgrv63c.onion:8000":
                return "@manfredkarrer";
            default:
                return "Undefined";
        }
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
}
