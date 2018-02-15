package io.bisq.core.btc.wallet;

import io.bisq.core.btc.BitcoinNodes;
import io.bisq.core.btc.BitcoinNodes.BtcNode;
import io.bisq.core.user.Preferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static io.bisq.core.btc.BitcoinNodes.BitcoinNodesOption.CUSTOM;
import static io.bisq.core.btc.BitcoinNodes.BitcoinNodesOption.PUBLIC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Preferences.class)
public class WalletSetupPreferencesTest {
    @Test
    public void testSelectPreferredNodesWhenPublicOption() {
        Preferences delegate = mock(Preferences.class);
        when(delegate.getBitcoinNodesOptionOrdinal()).thenReturn(PUBLIC.ordinal());

        WalletSetupPreferences preferences = new WalletSetupPreferences(delegate);
        List<BtcNode> nodes = preferences.selectPreferredNodes(mock(BitcoinNodes.class));

        assertTrue(nodes.isEmpty());
    }

    @Test
    public void testSelectPreferredNodesWhenCustomOption() {
        Preferences delegate = mock(Preferences.class);
        when(delegate.getBitcoinNodesOptionOrdinal()).thenReturn(CUSTOM.ordinal());
        when(delegate.getBitcoinNodes()).thenReturn("aaa.onion,bbb.onion");

        WalletSetupPreferences preferences = new WalletSetupPreferences(delegate);
        List<BtcNode> nodes = preferences.selectPreferredNodes(mock(BitcoinNodes.class));

        assertEquals(2, nodes.size());
    }
}
