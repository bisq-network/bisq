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

package bisq.core.app;

import bisq.core.api.CoreContext;
import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.locale.Res;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import bisq.common.config.Config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The footer names the local Bitcoin node only when the wallet uses it.
public class WalletAppSetupTest {
    private final Preferences preferences = mock(Preferences.class);
    private final LocalBitcoinNode localBitcoinNode = mock(LocalBitcoinNode.class);

    @BeforeEach
    public void setUp() {
        Res.setup();
    }

    @Test
    public void usedLocalNodeIsShown() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(true);

        assertEquals(Res.get("BTC_MAINNET") + " " + Res.get("mainView.footer.localhostBitcoinNode"),
                walletAppSetup(new Config()).getBtcNetworkAsString());
    }

    @Test
    public void ignoredLocalNodeIsNotShown() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(false);
        when(preferences.getUseTorForBitcoinJ()).thenReturn(true);

        assertEquals(Res.get("BTC_MAINNET") + " " + Res.get("mainView.footer.usingTor"),
                walletAppSetup(new Config("--ignoreLocalBtcNode=true")).getBtcNetworkAsString());
    }

    @Test
    public void absentLocalNodeIsNotShown() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(false);

        assertEquals(Res.get("BTC_MAINNET"), walletAppSetup(new Config()).getBtcNetworkAsString());
    }

    private WalletAppSetup walletAppSetup(Config config) {
        return new WalletAppSetup(mock(CoreContext.class), mock(WalletsManager.class), mock(WalletsSetup.class),
                mock(FeeService.class), config, preferences, localBitcoinNode);
    }
}
