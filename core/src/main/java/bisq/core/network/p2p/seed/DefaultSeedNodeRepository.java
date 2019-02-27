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
import bisq.network.p2p.seed.SeedNodeRepository;

import javax.inject.Inject;

import java.util.Set;
import java.util.stream.Stream;

public class DefaultSeedNodeRepository implements SeedNodeRepository {

    private final Set<NodeAddress> seedNodeAddresses;
    private final Set<NodeAddress> torSeedNodeAddresses;
    private final Set<NodeAddress> localhostSeedNodeAddresses;

    @Inject
    public DefaultSeedNodeRepository(SeedNodeAddressLookup lookup) {
        this.seedNodeAddresses = lookup.resolveNodeAddresses();
        this.torSeedNodeAddresses = DefaultSeedNodeAddresses.DEFAULT_TOR_SEED_NODE_ADDRESSES;
        this.localhostSeedNodeAddresses = DefaultSeedNodeAddresses.DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES;
    }

    @Override
    public Set<NodeAddress> getSeedNodeAddresses() {
        return seedNodeAddresses;
    }

    @Override
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

    @Override
    public boolean isSeedNode(NodeAddress nodeAddress) {
        return Stream.concat(localhostSeedNodeAddresses.stream(), torSeedNodeAddresses.stream())
                .anyMatch(e -> e.equals(nodeAddress));
    }
}
