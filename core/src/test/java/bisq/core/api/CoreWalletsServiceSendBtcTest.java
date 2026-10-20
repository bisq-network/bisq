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
import bisq.core.btc.wallet.WithdrawalWalletFixture;
import bisq.core.dao.DaoFacade;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sendbtc API call with the real BtcWalletService and a real bitcoinj wallet: the API treats the amount as
 * including the fee and sends the amounts of the fee estimation.
 */
class CoreWalletsServiceSendBtcTest {
    private final WithdrawalWalletFixture fixture = new WithdrawalWalletFixture(Coin.valueOf(50));
    private CoreWalletsService coreWalletsService;

    @BeforeEach
    void setUp() {
        AppStartupState appStartupState = mock(AppStartupState.class);
        when(appStartupState.isWalletAndNetworkReady()).thenReturn(true);
        WalletsManager walletsManager = mock(WalletsManager.class);
        when(walletsManager.areWalletsAvailable()).thenReturn(true);
        coreWalletsService = new CoreWalletsService(appStartupState,
                mock(CoreContext.class),
                mock(Balances.class),
                walletsManager,
                mock(BsqWalletService.class),
                mock(BsqTransferService.class),
                mock(BsqFormatter.class),
                fixture.getBtcWalletService(),
                new ImmutableCoinFormatter(WithdrawalWalletFixture.PARAMS.getMonetaryFormat()),
                fixture.getFeeService(),
                mock(DaoFacade.class),
                mock(Preferences.class));
    }

    // The 300 satoshi left of the inputs are below bitcoinj's change limit and part of the fee once
    @Test
    void aRemainderThatBitcoinjAddsToTheFeeIsPaidOnce() {
        fixture.fund(8000);

        Transaction sentTx = sendBtc("0.000077");

        assertSent(sentTx, 2200, 5800, 1);
    }

    // The 540 satoshi left of the inputs are above bitcoinj's change limit of 537 but below our dust limit
    @Test
    void aChangeOutputBelowOurDustLimitIsPartOfTheFee() {
        fixture.fund(8240);

        Transaction sentTx = sendBtc("0.000077");

        assertSent(sentTx, 2200, 6040, 1);
    }

    @Test
    void anAmountWellBelowTheInputsLeavesAChangeOutput() {
        fixture.fund(100_000);

        Transaction sentTx = sendBtc("0.0005");

        assertSent(sentTx, 42_950, 7050, 2);
    }

    private Transaction sendBtc(String amount) {
        assertThrows(WithdrawalWalletFixture.SendStoppedException.class,
                () -> coreWalletsService.sendBtc(fixture.getToAddress(), amount, "50", "", noCallback()));
        assertNotNull(fixture.getSentTx());
        return fixture.getSentTx();
    }

    private void assertSent(Transaction sentTx, long receiverAmount, long fee, int numOutputs) {
        assertEquals(Coin.valueOf(receiverAmount), sentTx.getOutput(0).getValue());
        assertEquals(fixture.getToAddress(),
                sentTx.getOutput(0).getScriptPubKey().getToAddress(WithdrawalWalletFixture.PARAMS).toString());
        assertEquals(Coin.valueOf(fee), sentTx.getFee());
        assertEquals(numOutputs, sentTx.getOutputs().size());
    }

    private static FutureCallback<Transaction> noCallback() {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
            }
        };
    }
}
