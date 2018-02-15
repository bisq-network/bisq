/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

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

public class NodeAddressLookup {
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
        NodeAddresses allNodeAddresses = getAllAddresses();

        Set<String> bannedHosts = getBannedHosts();
        allNodeAddresses = allNodeAddresses.excludeByHost(bannedHosts);

        if (myAddress != null) {
            allNodeAddresses = allNodeAddresses.excludeByFullAddress(myAddress);
        }

        log.debug("We received banned seed nodes={}, seedNodeAddresses={}", bannedHosts, allNodeAddresses);
        return allNodeAddresses;
    }

    private Set<String> getBannedHosts() {
        return Optional.ofNullable(environment.getBannedSeedNodes())
                .map(HashSet::new)
                .map(hosts -> (Set<String>) hosts)
                .orElse(Collections.emptySet());
    }

    private NodeAddresses getAllAddresses() {
        NodeAddresses nodeAddresses = Optional.ofNullable(seedNodes)
                .map(nodes -> NodeAddresses.fromString(seedNodes))
                .orElse(new NodeAddresses(Collections.emptySet()));

        if (nodeAddresses.isEmpty()) {
            Set<NodeAddress> delegate = isLocalHostUsed
                    ? DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES
                    : DEFAULT_TOR_SEED_NODE_ADDRESSES;
            nodeAddresses = delegate.stream()
                    .filter(address -> isAddressFromNetwork(address, networkId))
                    .collect(NodeAddresses.collector());
        }
        return nodeAddresses;
    }

    private static boolean isAddressFromNetwork(NodeAddress address, int networkId) {
        String suffix = "0" + networkId;
        int port = address.getPort();
        String portAsString = String.valueOf(port);
        return portAsString.endsWith(suffix);
    }
}
