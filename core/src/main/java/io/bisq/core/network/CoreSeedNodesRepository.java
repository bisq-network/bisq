package io.bisq.core.network;

import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.seed.SeedNodesRepository;

import javax.inject.Inject;
import java.util.Set;
import java.util.stream.Stream;

public class CoreSeedNodesRepository implements SeedNodesRepository {

    private final Set<NodeAddress> seedNodeAddresses;
    private final Set<NodeAddress> torSeedNodeAddresses;
    private final Set<NodeAddress> localhostSeedNodeAddresses;

    @Inject
    public CoreSeedNodesRepository(NodeAddressLookup lookup) {
        this.seedNodeAddresses = lookup.resolveNodeAddresses();
        this.torSeedNodeAddresses = DefaultNodeAddresses.DEFAULT_TOR_SEED_NODE_ADDRESSES;
        this.localhostSeedNodeAddresses = DefaultNodeAddresses.DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES;
    }

    @Override
    public Set<NodeAddress> getSeedNodeAddresses() {
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

    public boolean isSeedNode(NodeAddress nodeAddress) {
        return Stream.concat(localhostSeedNodeAddresses.stream(), torSeedNodeAddresses.stream())
                .anyMatch(e -> e.equals(nodeAddress));
    }
}
