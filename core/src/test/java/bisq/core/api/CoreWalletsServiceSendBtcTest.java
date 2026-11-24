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

package bisq.core.api;

import bisq.core.app.AppStartupState;
import bisq.core.btc.Balances;
import bisq.core.btc.wallet.BsqTransferService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.WithdrawalTestWallet;
import bisq.core.dao.DaoFacade;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.core.btc.wallet.WithdrawalTestWallet.PARAMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sendbtc API call on a real bitcoinj wallet that stops the send before the commit. The amount includes the fee.
 */
class CoreWalletsServiceSendBtcTest {
    private final WithdrawalTestWallet testWallet = new WithdrawalTestWallet();
    private CoreWalletsService coreWalletsService;

    @BeforeEach
    void setUp() {
        AppStartupState appStartupState = mock(AppStartupState.class);
        when(appStartupState.isWalletAndNetworkReady()).thenReturn(true);
        WalletsManager walletsManager = mock(WalletsManager.class);
        when(walletsManager.areWalletsAvailable()).thenReturn(true);
        when(testWallet.getFeeService().getMinFeePerVByte()).thenReturn(2L);
        coreWalletsService = new CoreWalletsService(appStartupState,
                new CoreContext(),
                mock(Balances.class),
                walletsManager,
                mock(BsqWalletService.class),
                mock(BsqTransferService.class),
                mock(BsqFormatter.class),
                testWallet.getBtcWalletService(),
                new ImmutableCoinFormatter(PARAMS.getMonetaryFormat()),
                testWallet.getFeeService(),
                mock(DaoFacade.class),
                mock(Preferences.class));
    }

    @Test
    void theReceiverGetsTheAmountMinusTheFee() {
        testWallet.fund(100_000);

        Transaction sentTx = sendBtc("0.0005", "50");

        assertSent(sentTx, 42_950, 7050, 2);
    }

    @Test
    void aFeeRateAtTheMinimumIsAccepted() {
        testWallet.fund(100_000);

        Transaction sentTx = sendBtc("0.0005", "2");

        assertSent(sentTx, 49_718, 282, 2);
    }

    @Test
    void aFeeRateBelowTheMinimumIsRefused() {
        testWallet.fund(100_000);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> coreWalletsService.sendBtc(testWallet.getToAddress(), "0.0005", "1", "",
                        WithdrawalTestWallet.noCallback()));

        assertEquals("tx fee rate must be >= 2 sat/vbyte", exception.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> coreWalletsService.sendBtc(testWallet.getToAddress(), "0.0005", "-10", "",
                        WithdrawalTestWallet.noCallback()));
        assertEquals(0, testWallet.getCompletedTxs());
    }

    private Transaction sendBtc(String amount, String txFeeRate) {
        assertThrows(WithdrawalTestWallet.SendStoppedException.class,
                () -> coreWalletsService.sendBtc(testWallet.getToAddress(), amount, txFeeRate, "",
                        WithdrawalTestWallet.noCallback()));
        Transaction sentTx = testWallet.getSentTx();
        assertNotNull(sentTx);
        return sentTx;
    }

    private void assertSent(Transaction sentTx, long receiverAmount, long fee, int numOutputs) {
        assertEquals(Coin.valueOf(receiverAmount), sentTx.getOutput(0).getValue());
        assertEquals(testWallet.getToAddress(), sentTx.getOutput(0).getScriptPubKey().getToAddress(PARAMS).toString());
        assertEquals(Coin.valueOf(fee), sentTx.getFee());
        assertEquals(numOutputs, sentTx.getOutputs().size());
    }
}
