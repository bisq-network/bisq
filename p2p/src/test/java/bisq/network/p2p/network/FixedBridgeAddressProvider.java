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

// The bridges of a test, instead of the preferences of the user
class FixedBridgeAddressProvider implements BridgeAddressProvider {
    private final BridgeOption bridgeOption;
    private final BridgeTransport bridgeTransport;
    @Nullable
    private final List<String> customBridgeLines;

    FixedBridgeAddressProvider(BridgeOption bridgeOption,
                               BridgeTransport bridgeTransport,
                               @Nullable List<String> customBridgeLines) {
        this.bridgeOption = bridgeOption;
        this.bridgeTransport = bridgeTransport;
        this.customBridgeLines = customBridgeLines;
    }

    @Override
    public BridgeOption getBridgeOption() {
        return bridgeOption;
    }

    @Override
    public BridgeTransport getBridgeTransport() {
        return bridgeTransport;
    }

    @Override
    @Nullable
    public List<String> getCustomBridgeLines() {
        return customBridgeLines;
    }
}
