package io.bisq.core.btc.wallet;

import io.bisq.common.util.Utilities;
import io.bisq.core.btc.BitcoinNodes;
import io.bisq.core.btc.BitcoinNodes.BitcoinNodesOption;
import io.bisq.core.btc.BitcoinNodes.BtcNode;
import io.bisq.core.user.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static io.bisq.core.btc.BitcoinNodes.BitcoinNodesOption.CUSTOM;
import static io.bisq.core.btc.wallet.WalletsSetup.DEFAULT_CONNECTIONS;


class WalletSetupPreferences {
    private static final Logger log = LoggerFactory.getLogger(WalletSetupPreferences.class);

    private final Preferences preferences;

    WalletSetupPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    List<BtcNode> selectPreferredNodes(BitcoinNodes bitcoinNodes) {
        List<BtcNode> result;

        BitcoinNodesOption nodesOption = BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()];
        switch (nodesOption) {
            case CUSTOM:
                result = BitcoinNodes.toBtcNodesList(Utilities.commaSeparatedListToSet(preferences.getBitcoinNodes(),
                        false));
                if (result.isEmpty()) {
                    log.warn("Custom nodes is set but no valid nodes are provided. " +
                            "We fall back to provided nodes option.");
                    preferences.setBitcoinNodesOptionOrdinal(BitcoinNodesOption.PROVIDED.ordinal());
                    result = bitcoinNodes.getProvidedBtcNodes();
                }
                break;
            case PUBLIC:
                result = Collections.emptyList();
                break;
            case PROVIDED:
            default:
                result = bitcoinNodes.getProvidedBtcNodes();
                break;
        }

        return result;
    }

    boolean isUseCustomNodes() {
        return CUSTOM.ordinal() == preferences.getBitcoinNodesOptionOrdinal();
    }

    void calculateMinBroadcastConnections(WalletConfig walletConfig, List<BtcNode> nodes) {
        BitcoinNodesOption nodesOption = BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()];
        switch (nodesOption) {
            case CUSTOM:
                // We have set the nodes already above
                walletConfig.setMinBroadcastConnections((int) Math.ceil(nodes.size() * 0.5));
                // If Tor is set we usually only use onion nodes,
                // but if user provides mixed clear net and onion nodes we want to use both
                break;
            case PUBLIC:
                // We keep the empty nodes
                walletConfig.setMinBroadcastConnections((int) Math.floor(DEFAULT_CONNECTIONS * 0.8));
                break;
            case PROVIDED:
            default:
                // We require only 4 nodes instead of 7 (for 9 max connections) because our provided nodes
                // are more reliable than random public nodes.
                walletConfig.setMinBroadcastConnections(4);
                break;
        }
    }

}
