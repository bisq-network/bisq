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

package bisq.core.btc.wallet;

import bisq.core.btc.BitcoinNodes;
import bisq.core.btc.BitcoinNodes.BitcoinNodesOption;
import bisq.core.btc.BitcoinNodes.BtcNode;
import bisq.core.user.Preferences;

import bisq.common.util.Utilities;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.core.btc.BitcoinNodes.BitcoinNodesOption.CUSTOM;
import static bisq.core.btc.wallet.WalletsSetup.DEFAULT_CONNECTIONS;


class WalletSetupPreferences {
    private static final Logger log = LoggerFactory.getLogger(WalletSetupPreferences.class);

    private final Preferences preferences;

    WalletSetupPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    List<BtcNode> selectPreferredNodes(BitcoinNodes nodes) {
        List<BtcNode> result;

        BitcoinNodesOption nodesOption = BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()];
        switch (nodesOption) {
            case CUSTOM:
                String bitcoinNodes = preferences.getBitcoinNodes();
                Set<String> distinctNodes = Utilities.commaSeparatedListToSet(bitcoinNodes, false);
                result = BitcoinNodes.toBtcNodesList(distinctNodes);
                if (result.isEmpty()) {
                    log.warn("Custom nodes is set but no valid nodes are provided. " +
                            "We fall back to provided nodes option.");
                    preferences.setBitcoinNodesOptionOrdinal(BitcoinNodesOption.PROVIDED.ordinal());
                    result = nodes.getProvidedBtcNodes();
                }
                break;
            case PUBLIC:
                result = Collections.emptyList();
                break;
            case PROVIDED:
            default:
                result = nodes.getProvidedBtcNodes();
                break;
        }

        return result;
    }

    boolean isUseCustomNodes() {
        return CUSTOM.ordinal() == preferences.getBitcoinNodesOptionOrdinal();
    }

    int calculateMinBroadcastConnections(List<BtcNode> nodes) {
        BitcoinNodesOption nodesOption = BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()];
        int result;
        switch (nodesOption) {
            case CUSTOM:
                // We have set the nodes already above
                result = (int) Math.ceil(nodes.size() * 0.5);
                // If Tor is set we usually only use onion nodes,
                // but if user provides mixed clear net and onion nodes we want to use both
                break;
            case PUBLIC:
                // We keep the empty nodes
                result = (int) Math.floor(DEFAULT_CONNECTIONS * 0.8);
                break;
            case PROVIDED:
            default:
                // We require only 4 nodes instead of 7 (for 9 max connections) because our provided nodes
                // are more reliable than random public nodes.
                result = 4;
                break;
        }
        return result;
    }

}
