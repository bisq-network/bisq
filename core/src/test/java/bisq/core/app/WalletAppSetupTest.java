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
import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.nodes.BtcNodes;
import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.setup.RegTestHost;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.filter.DenyList;
import bisq.core.locale.Res;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import bisq.network.Socks5ProxyProvider;

import bisq.common.config.Config;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The footer names the local Bitcoin node only when the wallet uses it, as decided by WalletsSetup.
public class WalletAppSetupTest {
    private final Preferences preferences = mock(Preferences.class);
    private final LocalBitcoinNode localBitcoinNode = mock(LocalBitcoinNode.class);

    @TempDir
    Path appDataDir;

    @BeforeEach
    public void setUp() {
        Res.setup();
    }

    // Config keeps the network in a static field, which other tests read
    @AfterEach
    public void tearDown() {
        config();
    }

    @Test
    public void usedLocalNodeIsShown() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(true);

        assertEquals(Res.get("BTC_MAINNET") + " " + Res.get("mainView.footer.localhostBitcoinNode"),
                walletAppSetup(config(), RegTestHost.NONE).getBtcNetworkAsString());
    }

    @Test
    public void ignoredLocalNodeIsNotShown() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(false);
        when(preferences.getUseTorForBitcoinJ()).thenReturn(true);

        assertEquals(Res.get("BTC_MAINNET") + " " + Res.get("mainView.footer.usingTor"),
                walletAppSetup(config("--ignoreLocalBtcNode=true"), RegTestHost.NONE).getBtcNetworkAsString());
    }

    @Test
    public void absentLocalNodeIsNotShown() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(false);

        assertEquals(Res.get("BTC_MAINNET"), walletAppSetup(config(), RegTestHost.NONE).getBtcNetworkAsString());
    }

    // On regtest the wallet connects to the regtest host, which is localhost here, also with --ignoreLocalBtcNode=true
    @Test
    public void regtestWithTheLocalRegtestHostShowsTheLocalNode() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(false);

        assertEquals(Res.get("BTC_REGTEST") + " " + Res.get("mainView.footer.localhostBitcoinNode"),
                walletAppSetup(config("--baseCurrencyNetwork=BTC_REGTEST", "--ignoreLocalBtcNode=true"),
                        RegTestHost.LOCALHOST).getBtcNetworkAsString());
    }

    // bitcoinj connects to a detected local node first, also if another regtest host is configured
    @Test
    public void regtestWithARemoteHostAndADetectedLocalNodeShowsTheLocalNode() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(true);

        assertEquals(Res.get("BTC_REGTEST") + " " + Res.get("mainView.footer.localhostBitcoinNode"),
                walletAppSetup(config("--baseCurrencyNetwork=BTC_REGTEST"), RegTestHost.REMOTE_HOST)
                        .getBtcNetworkAsString());
    }

    @Test
    public void regtestWithARemoteHostDoesNotShowTheLocalNode() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(false);

        assertEquals(Res.get("BTC_REGTEST"),
                walletAppSetup(config("--baseCurrencyNetwork=BTC_REGTEST"), RegTestHost.REMOTE_HOST)
                        .getBtcNetworkAsString());
    }

    @Test
    public void regtestWithoutARegtestHostDoesNotShowTheLocalNode() {
        when(localBitcoinNode.shouldBeUsed()).thenReturn(false);

        assertEquals(Res.get("BTC_REGTEST"),
                walletAppSetup(config("--baseCurrencyNetwork=BTC_REGTEST"), RegTestHost.NONE)
                        .getBtcNetworkAsString());
    }

    private Config config(String... args) {
        String[] allArgs = new String[args.length + 1];
        allArgs[0] = "--appDataDir=" + appDataDir;
        System.arraycopy(args, 0, allArgs, 1, args.length);
        return new Config(allArgs);
    }

    private WalletAppSetup walletAppSetup(Config config, RegTestHost regTestHost) {
        WalletsSetup walletsSetup = new WalletsSetup(regTestHost, mock(AddressEntryList.class), preferences,
                mock(Socks5ProxyProvider.class), config, mock(DenyList.class), localBitcoinNode, mock(BtcNodes.class),
                "Bisq", appDataDir.toFile(), false, 9, "ALL");
        return new WalletAppSetup(mock(CoreContext.class), mock(WalletsManager.class), walletsSetup,
                mock(FeeService.class), config, preferences);
    }
}
