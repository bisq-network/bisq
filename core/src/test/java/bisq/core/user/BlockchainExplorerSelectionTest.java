package bisq.core.user;

import bisq.common.persistence.PersistenceManager;

import java.util.ArrayList;
import java.util.List;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class BlockchainExplorerSelectionTest {

    private static final List<BlockChainExplorer> ALL_BTC_EXPLORERS = new ArrayList<>(List.of(
            new BlockChainExplorer("alice.space (@alice)", "https://alice.space/tx/", "https://alice.space/address/"),
            new BlockChainExplorer("bob.de (@bob)", "https://bob.onion/tx/", "https://bob.onion/address/"),
            new BlockChainExplorer("charlie.info (@charlie)", "https://charlie.info/tx/", "https://charlie.info/address/")));

    private static final List<BlockChainExplorer> ALL_BSQ_EXPLORERS = new ArrayList<>(List.of(
            new BlockChainExplorer("alice.bisq.space (@alice)", "https://alice.bisq.space/tx/", "https://alice.bisq.space/address/"),
            new BlockChainExplorer("bob.bisq.de (@bob)", "https://bob.bisq.onion/tx/", "https://bob.bisq.onion/address/"),
            new BlockChainExplorer("charlie.bisq.info (@charlie)", "https://charlie.bisq.info/tx/", "https://charlie.bisq.info/address/")));

    @Mock
    Preferences preferences;

    @BeforeEach
    void setup() {
        doReturn(ALL_BTC_EXPLORERS).when(preferences).getBlockChainExplorers();
        doReturn(ALL_BSQ_EXPLORERS).when(preferences).getBsqBlockChainExplorers();
    }

    @Test
    void noBtcExploreSet() {
        @SuppressWarnings("unchecked")
        var explorerSelection = new BlockchainExplorerSelection(preferences, mock(PreferencesPayload.class),
                mock(PersistenceManager.class));
        explorerSelection.selectNodes();

        BlockChainExplorer expectedBtcNode = ALL_BTC_EXPLORERS.get(0);
        verify(preferences).setBlockChainExplorer(expectedBtcNode);
    }

    @Test
    void noBsqExploreSet() {
        @SuppressWarnings("unchecked")
        var explorerSelection = new BlockchainExplorerSelection(preferences, mock(PreferencesPayload.class),
                mock(PersistenceManager.class));
        explorerSelection.selectNodes();

        ArgumentCaptor<BlockChainExplorer> argumentCaptor = ArgumentCaptor.forClass(BlockChainExplorer.class);
        verify(preferences).setBsqBlockChainExplorer(argumentCaptor.capture());
        BlockChainExplorer selectedExplorer = argumentCaptor.getValue();

        assertThat(ALL_BSQ_EXPLORERS, hasItems(selectedExplorer));
    }

    @Test
    void deprecatedBsqExplorerSelected() {
        @SuppressWarnings("unchecked")
        var explorerSelection = new BlockchainExplorerSelection(preferences, mock(PreferencesPayload.class),
                mock(PersistenceManager.class));

        BlockChainExplorer selectedExplorer = mock(BlockChainExplorer.class);
        doReturn("https://bisq.mempool.emzy.de/tx/").when(selectedExplorer).getTxUrl();
        doReturn(selectedExplorer).when(preferences).getBsqBlockChainExplorer();

        explorerSelection.selectNodes();

        ArgumentCaptor<BlockChainExplorer> argumentCaptor = ArgumentCaptor.forClass(BlockChainExplorer.class);
        verify(preferences).setBsqBlockChainExplorer(argumentCaptor.capture());
        BlockChainExplorer newExplorer = argumentCaptor.getValue();

        assertThat(ALL_BSQ_EXPLORERS, hasItems(newExplorer));
    }

    @Test
    void noAutoConfirmSelected(@Mock PreferencesPayload prefPayload,
                               @SuppressWarnings("rawtypes") @Mock PersistenceManager persistenceManager) {
        @SuppressWarnings("unchecked")
        var explorerSelection = new BlockchainExplorerSelection(preferences, prefPayload, persistenceManager);

        List<String> defaultXmrProofServices = List.of("alice-monero-xmr-proof-service.onion",
                "bob-monero-xmr-proof-service.onion");
        doReturn(defaultXmrProofServices).when(preferences).getDefaultXmrTxProofServices();

        List<AutoConfirmSettings> autoConfirmSettingsList = new ArrayList<>();
        doReturn(autoConfirmSettingsList).when(prefPayload).getAutoConfirmSettingsList();
        doReturn(autoConfirmSettingsList).when(preferences).getAutoConfirmSettingsList();

        explorerSelection.selectNodes();

        assertThat(autoConfirmSettingsList, hasSize(1));
        AutoConfirmSettings confirmSettings = autoConfirmSettingsList.get(0);
        assertThat(confirmSettings.getServiceAddresses(),
                contains(defaultXmrProofServices.get(0), defaultXmrProofServices.get(1)));

        verify(persistenceManager).forcePersistNow();
    }

    @Test
    void blacklistedAutoConfirmSelected(@Mock PreferencesPayload prefPayload,
                                        @SuppressWarnings("rawtypes") @Mock PersistenceManager persistenceManager) {
        @SuppressWarnings("unchecked")
        var explorerSelection = new BlockchainExplorerSelection(preferences, prefPayload, persistenceManager);

        List<String> defaultXmrProofServices = List.of("alice-monero-xmr-proof-service.onion",
                "bob-monero-xmr-proof-service.onion");
        doReturn(defaultXmrProofServices).when(preferences).getDefaultXmrTxProofServices();

        List<String> serviceAddresses = List.of("monero3bec7m26vx6si6qo7q7imlaoz45ot5m2b5z2ppgoooo6jx2rqd.onion");
        var autoConfirmSettings = new AutoConfirmSettings(true, 6,
                1, serviceAddresses, "XMR");

        List<AutoConfirmSettings> autoConfirmSettingsList = new ArrayList<>(List.of(autoConfirmSettings));
        doReturn(autoConfirmSettingsList).when(prefPayload).getAutoConfirmSettingsList();
        doReturn(autoConfirmSettingsList).when(preferences).getAutoConfirmSettingsList();

        explorerSelection.selectNodes();

        assertThat(autoConfirmSettingsList, hasSize(1));
        AutoConfirmSettings confirmSettings = autoConfirmSettingsList.get(0);
        assertThat(confirmSettings.getServiceAddresses(),
                contains(defaultXmrProofServices.get(0), defaultXmrProofServices.get(1)));

        verify(persistenceManager).forcePersistNow();
    }
}
