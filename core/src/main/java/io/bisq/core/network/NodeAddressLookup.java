package io.bisq.core.network;

import com.google.inject.name.Named;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.p2p.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static io.bisq.core.network.DefaultNodeAddresses.DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES;
import static io.bisq.core.network.DefaultNodeAddresses.DEFAULT_TOR_SEED_NODE_ADDRESSES;

// TODO bind
class NodeAddressLookup {
    private static final Logger log = LoggerFactory.getLogger(NodeAddressLookup.class);

    private final BisqEnvironment environment;
    private final boolean isLocalHostUsed;
    private final int networkId;
    @Nullable
    private final String myAddress;
    @Nullable
    private final String seedNodes;

    @Inject
    NodeAddressLookup(BisqEnvironment environment,
                      @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P,
                      @Named(NetworkOptionKeys.NETWORK_ID) int networkId,
                      @Nullable @Named(NetworkOptionKeys.MY_ADDRESS) String myAddress,
                      @Nullable @Named(NetworkOptionKeys.SEED_NODES_KEY) String seedNodes) {
        this.environment = environment;
        this.isLocalHostUsed = useLocalhostForP2P;
        this.networkId = networkId;
        this.myAddress = myAddress;
        this.seedNodes = seedNodes;
    }

    Set<NodeAddress> resolveNodeAddresses() {
        NodeAddresses nodeAddresses = NodeAddresses.fromString(seedNodes);
        if (nodeAddresses.isEmpty()) {
            Set<NodeAddress> delegate = isLocalHostUsed
                    ? DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES
                    : DEFAULT_TOR_SEED_NODE_ADDRESSES;
            nodeAddresses = NodeAddresses.fromSet(delegate, networkId);
        }

        Set<String> bannedHosts = getBannedHosts(environment);
        nodeAddresses = nodeAddresses.excludeByHost(bannedHosts);

        // TODO refactor when null
        nodeAddresses = nodeAddresses.excludeByFullAddress(myAddress);

        log.debug("We received banned seed nodes={}, seedNodeAddresses={}", bannedHosts, nodeAddresses);
        return nodeAddresses.toSet();
    }

    private Set<String> getBannedHosts(BisqEnvironment environment) {
        return Optional.ofNullable(environment.getBannedSeedNodes())
                .map(HashSet::new)
                .map(hosts -> (Set<String>) hosts)
                .orElse(Collections.emptySet());
    }
}
