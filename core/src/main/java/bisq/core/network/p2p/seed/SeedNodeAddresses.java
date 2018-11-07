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

import bisq.network.p2p.NodeAddress;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class SeedNodeAddresses extends ImmutableSetDecorator<NodeAddress> {

    public SeedNodeAddresses(Set<NodeAddress> delegate) {
        super(delegate);
    }

    public SeedNodeAddresses excludeByHost(Set<String> hosts) {
        Set<NodeAddress> copy = new HashSet<>(this);
        copy.removeIf(address -> {
            String hostName = address.getHostName();
            return hosts.contains(hostName);
        });
        return new SeedNodeAddresses(copy);
    }

    public SeedNodeAddresses excludeByFullAddress(String fullAddress) {
        Set<NodeAddress> copy = new HashSet<>(this);
        copy.removeIf(address -> fullAddress.equals(address.getFullAddress()));
        return new SeedNodeAddresses(copy);
    }

    public static Collector<NodeAddress, ?, SeedNodeAddresses> collector() {
        return Collectors.collectingAndThen(Collectors.toSet(), SeedNodeAddresses::new);
    }

    public static SeedNodeAddresses fromString(String seedNodes) {
        if (seedNodes.isEmpty()) {
            return new SeedNodeAddresses(Collections.emptySet());
        }

        String trimmed = StringUtils.deleteWhitespace(seedNodes);
        String[] nodes = trimmed.split(",");
        return Arrays.stream(nodes)
                .map(NodeAddress::new)
                .collect(collector());
    }
}
