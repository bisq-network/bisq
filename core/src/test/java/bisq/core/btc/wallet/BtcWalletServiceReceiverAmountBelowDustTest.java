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

import bisq.core.btc.exceptions.ReceiverAmountBelowDustException;
import bisq.core.btc.model.AddressEntry;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BtcWalletServiceReceiverAmountBelowDustTest {
    private static final Coin DUST_LIMIT = Restrictions.getMinNonDustOutput();

    private final WithdrawalTestWallet testWallet = new WithdrawalTestWallet();

    // Callers that do not handle it, such as the API, keep the error type and message
    @Test
    void aSendBelowTheDustLimitKeepsTheFormerError() {
        testWallet.fund(100_000);

        IllegalArgumentException exception = assertThrows(ReceiverAmountBelowDustException.class,
                () -> testWallet.send(DUST_LIMIT.add(Coin.valueOf(999)), Coin.valueOf(1000)));

        assertEquals("The amount is too low (dust limit).", exception.getMessage());
    }

    @Test
    void aSendAtTheDustLimitIsCompleted() throws Exception {
        testWallet.fund(100_000);

        assertEquals(DUST_LIMIT,
                testWallet.send(DUST_LIMIT.add(Coin.valueOf(1000)), Coin.valueOf(1000)).getOutput(0).getValue());
    }

    @Test
    void theFeeEstimationOfATradePayoutWithdrawalRefusesAnAmountBelowTheDustLimit() {
        testWallet.fund(100_000);
        String sourceAddress = testWallet.getSourceAddresses().iterator().next();

        assertThrows(ReceiverAmountBelowDustException.class,
                () -> testWallet.getBtcWalletService().getFeeEstimationTransaction(sourceAddress,
                        testWallet.getToAddress(),
                        DUST_LIMIT.subtract(Coin.SATOSHI),
                        AddressEntry.Context.AVAILABLE));
    }
}
