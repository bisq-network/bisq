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

package bisq.btcnodemonitor.btc;

import com.google.common.base.Joiner;

import java.util.LinkedList;
import java.util.List;


/**
 * Borrowed from VersionMessage and updated to be in sync with
 * https://github.com/bitcoin/bitcoin/blob/b1ba1b178f501daa1afdd91f9efec34e5ec1e294/src/protocol.h#L324
 */
public class ServiceBits {

    public static final int NODE_NONE = 0;
    // NODE_NETWORK means that the node is capable of serving the complete block chain. It is currently
    // set by all Bitcoin Core non pruned nodes, and is unset by SPV clients or other light clients.
    public static final int NODE_NETWORK = 1 << 0;

    // NODE_BLOOM means the node is capable and willing to handle bloom-filtered connections.
    public static final int NODE_BLOOM = 1 << 2;
    // NODE_WITNESS indicates that a node can be asked for blocks and transactions including
    // witness data.
    public static final int NODE_WITNESS = 1 << 3;
    // NODE_COMPACT_FILTERS means the node will service basic block filter requests.
    // See BIP157 and BIP158 for details on how this is implemented.
    public static final int NODE_COMPACT_FILTERS = 1 << 6;
    // NODE_NETWORK_LIMITED means the same as NODE_NETWORK with the limitation of only
    // serving the last 288 (2 day) blocks
    // See BIP159 for details on how this is implemented.
    public static final int NODE_NETWORK_LIMITED = 1 << 10;
    // NODE_P2P_V2 means the node supports BIP324 transport
    public static final int NODE_P2P_V2 = 1 << 11;

    public static String toString(long services) {
        List<String> strings = new LinkedList<>();
        if ((services & NODE_NETWORK) == NODE_NETWORK) {
            strings.add("NETWORK");
            services &= ~NODE_NETWORK;
        }
        if ((services & NODE_BLOOM) == NODE_BLOOM) {
            strings.add("BLOOM");
            services &= ~NODE_BLOOM;
        }
        if ((services & NODE_WITNESS) == NODE_WITNESS) {
            strings.add("WITNESS");
            services &= ~NODE_WITNESS;
        }
        if ((services & NODE_COMPACT_FILTERS) == NODE_COMPACT_FILTERS) {
            strings.add("COMPACT_FILTERS");
            services &= ~NODE_COMPACT_FILTERS;
        }
        if ((services & NODE_NETWORK_LIMITED) == NODE_NETWORK_LIMITED) {
            strings.add("NETWORK_LIMITED");
            services &= ~NODE_NETWORK_LIMITED;
        }
        if ((services & NODE_P2P_V2) == NODE_P2P_V2) {
            strings.add("NODE_P2P_V2");
            services &= ~NODE_P2P_V2;
        }
        if (services != 0)
            strings.add("Unrecognized service bit: " + Long.toBinaryString(services));
        return Joiner.on(", ").join(strings);
    }
}
