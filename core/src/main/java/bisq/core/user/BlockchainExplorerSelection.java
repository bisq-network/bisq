package bisq.core.user;

import bisq.common.persistence.PersistenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BlockchainExplorerSelection {


    private final Preferences preferences;
    private final PreferencesPayload prefPayload;
    private final PersistenceManager<PreferencesPayload> persistenceManager;

    public BlockchainExplorerSelection(Preferences preferences, PreferencesPayload prefPayload,
                                       PersistenceManager<PreferencesPayload> persistenceManager) {
        this.preferences = preferences;
        this.prefPayload = prefPayload;
        this.persistenceManager = persistenceManager;
    }

    public void selectNodes() {
        // if no valid Bitcoin block explorer is set, select the 1st valid Bitcoin block explorer
        ArrayList<BlockChainExplorer> btcExplorers = preferences.getBlockChainExplorers();
        if (preferences.getBlockChainExplorer() == null) {
            preferences.setBlockChainExplorer(btcExplorers.get(0));
        }

        // if no valid BSQ block explorer is set, randomly select a valid BSQ block explorer
        BlockChainExplorer currentBsqExplorer = preferences.getBsqBlockChainExplorer();
        if (currentBsqExplorer == null || isBsqBlockchainExplorerDeprecated(currentBsqExplorer)) {
            ArrayList<BlockChainExplorer> bsqExplorers = preferences.getBsqBlockChainExplorers();
            preferences.setBsqBlockChainExplorer(bsqExplorers.get((new Random()).nextInt(bsqExplorers.size())));
        }

        // Remove retired XMR AutoConfirm addresses
        List<String> retiredAddresses = List.of(
                "monero3bec7m26vx6si6qo7q7imlaoz45ot5m2b5z2ppgoooo6jx2rqd",
                "devinxmrwu4jrfq2zmq5kqjpxb44hx7i7didebkwrtvmvygj4uuop2ad"
        );
        var doApplyDefaults = prefPayload.getAutoConfirmSettingsList().stream()
                .map(autoConfirmSettings -> autoConfirmSettings.getServiceAddresses().stream()
                        .anyMatch(address -> retiredAddresses.stream()
                                .anyMatch(address::contains)))
                .findAny()
                .orElse(true);
        if (doApplyDefaults) {
            prefPayload.getAutoConfirmSettingsList().clear();
            List<String> defaultXmrTxProofServices = preferences.getDefaultXmrTxProofServices();
            AutoConfirmSettings.getDefault(defaultXmrTxProofServices, "XMR")
                    .ifPresent(xmrAutoConfirmSettings -> {
                        preferences.getAutoConfirmSettingsList().add(xmrAutoConfirmSettings);
                    });
            persistenceManager.forcePersistNow();
        }
    }

    private boolean isBsqBlockchainExplorerDeprecated(BlockChainExplorer blockChainExplorer) {
        String txUrl = blockChainExplorer.getTxUrl();
        return Preferences.DEPRECATED_BSQ_MAIN_NET_EXPLORERS.stream()
                .anyMatch(txUrl::contains);
    }
}
