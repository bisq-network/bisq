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

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BtcWalletServiceRefundPayoutTest {
    private static final Coin MAXIMUM_PAYOUT = Coin.valueOf(100_000);
    private static final Coin FEE = Coin.valueOf(1_000);

    @Test
    void acceptsNonNegativeOutputsWithinMaximum() {
        assertDoesNotThrow(() -> BtcWalletService.validateRefundPayoutAmounts(
                Coin.valueOf(60_000),
                Coin.valueOf(40_000),
                MAXIMUM_PAYOUT,
                FEE));
        assertDoesNotThrow(() -> BtcWalletService.validateRefundPayoutAmounts(
                MAXIMUM_PAYOUT,
                Coin.ZERO,
                MAXIMUM_PAYOUT,
                FEE));
    }

    @Test
    void rejectsNegativeOffsetAmounts() {
        assertThrows(IllegalArgumentException.class, () -> BtcWalletService.validateRefundPayoutAmounts(
                Coin.valueOf(150_000),
                Coin.valueOf(-50_000),
                MAXIMUM_PAYOUT,
                FEE));
        assertThrows(IllegalArgumentException.class, () -> BtcWalletService.validateRefundPayoutAmounts(
                Coin.valueOf(-50_000),
                Coin.valueOf(150_000),
                MAXIMUM_PAYOUT,
                FEE));
    }

    @Test
    void rejectsPositiveOutputsAboveMaximum() {
        assertThrows(IllegalArgumentException.class, () -> BtcWalletService.validateRefundPayoutAmounts(
                Coin.valueOf(100_000),
                Coin.valueOf(1),
                MAXIMUM_PAYOUT,
                FEE));
        assertThrows(IllegalArgumentException.class, () -> BtcWalletService.validateRefundPayoutAmounts(
                Coin.valueOf(Long.MAX_VALUE),
                Coin.SATOSHI,
                Coin.valueOf(Long.MAX_VALUE),
                FEE));
    }

    @Test
    void rejectsZeroPayoutAndNegativeFee() {
        assertThrows(IllegalArgumentException.class, () -> BtcWalletService.validateRefundPayoutAmounts(
                Coin.ZERO,
                Coin.ZERO,
                MAXIMUM_PAYOUT,
                FEE));
        assertThrows(IllegalArgumentException.class, () -> BtcWalletService.validateRefundPayoutAmounts(
                MAXIMUM_PAYOUT,
                Coin.ZERO,
                MAXIMUM_PAYOUT,
                Coin.valueOf(-1)));
    }
}
