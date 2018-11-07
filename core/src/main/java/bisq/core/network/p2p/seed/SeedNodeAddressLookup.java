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

package bisq.core.network.p2p.seed;

import bisq.core.app.BisqEnvironment;

import bisq.network.NetworkOptionKeys;
import bisq.network.p2p.NodeAddress;

import com.google.inject.name.Named;

import javax.inject.Inject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class SeedNodeAddressLookup {
    private static final Logger log = LoggerFactory.getLogger(SeedNodeAddressLookup.class);

    private final BisqEnvironment environment;
    private final boolean isLocalHostUsed;
    private final int networkId;
    @Nullable
    private final String myAddress;
    @Nullable
    private final String seedNodes;

    @Inject
    public SeedNodeAddressLookup(BisqEnvironment environment,
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

    public Set<NodeAddress> resolveNodeAddresses() {
        SeedNodeAddresses allSeedNodeAddresses = getAllAddresses();

        Set<String> bannedHosts = getBannedHosts();
        allSeedNodeAddresses = allSeedNodeAddresses.excludeByHost(bannedHosts);

        if (myAddress != null) {
            allSeedNodeAddresses = allSeedNodeAddresses.excludeByFullAddress(myAddress);
        }

        log.debug("We received banned seed nodes={}, seedNodeAddresses={}", bannedHosts, allSeedNodeAddresses);
        return allSeedNodeAddresses;
    }

    private Set<String> getBannedHosts() {
        return Optional.ofNullable(environment.getBannedSeedNodes())
                .map(HashSet::new)
                .map(hosts -> (Set<String>) hosts)
                .orElse(Collections.emptySet());
    }

    private SeedNodeAddresses getAllAddresses() {
        SeedNodeAddresses seedNodeAddresses = Optional.ofNullable(seedNodes)
                .map(nodes -> SeedNodeAddresses.fromString(seedNodes))
                .orElse(new SeedNodeAddresses(Collections.emptySet()));

        if (seedNodeAddresses.isEmpty()) {
            Set<NodeAddress> delegate = isLocalHostUsed
                    ? DefaultSeedNodeAddresses.DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES
                    : DefaultSeedNodeAddresses.DEFAULT_TOR_SEED_NODE_ADDRESSES;
            seedNodeAddresses = delegate.stream()
                    .filter(address -> isAddressFromNetwork(address, networkId))
                    .collect(SeedNodeAddresses.collector());
        }
        return seedNodeAddresses;
    }

    private static boolean isAddressFromNetwork(NodeAddress address, int networkId) {
        String suffix = "0" + networkId;
        int port = address.getPort();
        String portAsString = String.valueOf(port);
        return portAsString.endsWith(suffix);
    }
}
