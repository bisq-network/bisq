package io.bisq.core.network;

import com.google.inject.name.Named;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.p2p.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static io.bisq.core.network.DefaultNodeAddresses.DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES;
import static io.bisq.core.network.DefaultNodeAddresses.DEFAULT_TOR_SEED_NODE_ADDRESSES;

class CoreSeedNodeRepositoryFactory {
    private static final Logger log = LoggerFactory.getLogger(CoreSeedNodeRepositoryFactory.class);

    CoreSeedNodesRepository create(BisqEnvironment environment,
                                   @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P,
                                   @Named(NetworkOptionKeys.NETWORK_ID) int networkId,
                                   @Nullable @Named(NetworkOptionKeys.MY_ADDRESS) String myAddress,
                                   @Nullable @Named(NetworkOptionKeys.SEED_NODES_KEY) String seedNodes) {
        NodeAddresses nodeAddresses = NodeAddresses.fromString(seedNodes);
        if (nodeAddresses.isEmpty()) {
            Set<NodeAddress> delegate = useLocalhostForP2P
                    ? DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES
                    : DEFAULT_TOR_SEED_NODE_ADDRESSES;
            nodeAddresses = NodeAddresses.fromSet(delegate, networkId);
        }

        Set<String> bannedHosts = getBannedHosts(environment);
        nodeAddresses = nodeAddresses.excludeByHost(bannedHosts);

        nodeAddresses = nodeAddresses.excludeByFullAddress(myAddress);

        log.debug("We received banned seed nodes={}, seedNodeAddresses={}", bannedHosts, nodeAddresses);
        return new CoreSeedNodesRepository(nodeAddresses.toSet());
    }

    private Set<String> getBannedHosts(BisqEnvironment environment) {
        return Optional.ofNullable(environment.getBannedSeedNodes())
                .map(HashSet::new)
                .map(hosts -> (Set<String>) hosts)
                .orElse(Collections.emptySet());
    }
}
