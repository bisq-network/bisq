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


package bisq.network.p2p.network;

import java.util.List;

import javax.annotation.Nullable;

/**
 * The bridges which the user chose in the Tor network settings.
 */
public interface BridgeAddressProvider {
    // For applications without Tor network settings
    BridgeAddressProvider NONE = new BridgeAddressProvider() {
        @Override
        public BridgeOption getBridgeOption() {
            return BridgeOption.NONE;
        }

        @Override
        public BridgeTransport getBridgeTransport() {
            return BridgeTransport.OBFS_4;
        }

        @Override
        @Nullable
        public List<String> getCustomBridgeLines() {
            return null;
        }
    };

    BridgeOption getBridgeOption();

    // The type of the built-in bridges, which the node uses with BridgeOption.PROVIDED
    BridgeTransport getBridgeTransport();

    // The lines which the user entered, which the node uses with BridgeOption.CUSTOM
    @Nullable
    List<String> getCustomBridgeLines();
}
