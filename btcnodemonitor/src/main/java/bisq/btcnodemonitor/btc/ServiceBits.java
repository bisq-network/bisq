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

import static org.bitcoinj.core.VersionMessage.*;

/**
 * Borrowed from VersionMessage and added NODE_P2P_V2
 */
public class ServiceBits {
    public static final int NODE_P2P_V2 = 1 << 11;

    public static String toString(long services) {
        List<String> strings = new LinkedList<>();
        if ((services & NODE_NETWORK) == NODE_NETWORK) {
            strings.add("NETWORK");
            services &= ~NODE_NETWORK;
        }
        if ((services & NODE_GETUTXOS) == NODE_GETUTXOS) {
            strings.add("GETUTXOS");
            services &= ~NODE_GETUTXOS;
        }
        if ((services & NODE_BLOOM) == NODE_BLOOM) {
            strings.add("BLOOM");
            services &= ~NODE_BLOOM;
        }
        if ((services & NODE_WITNESS) == NODE_WITNESS) {
            strings.add("WITNESS");
            services &= ~NODE_WITNESS;
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
