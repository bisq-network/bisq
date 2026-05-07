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

package bisq.core.trade.validation;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.config.Config;

import com.google.common.annotations.VisibleForTesting;

import static bisq.core.util.Validator.checkIsPositive;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class DelayedPayoutTxValidation {
    public static final int MAX_LOCKTIME_BLOCK_DEVIATION = 3; // Peers latest block height must inside a +/- 3 blocks tolerance to ours.

    private DelayedPayoutTxValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Burning Man selection height
    /* --------------------------------------------------------------------- */

    public static int checkBurningManSelectionHeight(int burningManSelectionHeight,
                                                     DelayedPayoutTxReceiverService delayedPayoutTxReceiverService) {
        checkArgument(burningManSelectionHeight > 0,
                "burningManSelectionHeight must be positive");
        checkNotNull(delayedPayoutTxReceiverService, "delayedPayoutTxReceiverService must not be null");

        int expectedBurningManSelectionHeight = delayedPayoutTxReceiverService.getBurningManSelectionHeight();
        checkArgument(expectedBurningManSelectionHeight > 0,
                "expectedBurningManSelectionHeight must be positive");

        if (burningManSelectionHeight != expectedBurningManSelectionHeight) {
            // Allow SNAPSHOT_SELECTION_GRID_SIZE (10 blocks) as tolerance if traders had different heights.
            int diff = Math.abs(burningManSelectionHeight - expectedBurningManSelectionHeight);
            checkArgument(diff == DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE,
                    "If Burning Man selection heights are not the same they have to differ by " +
                            "exactly the snapshot grid size, otherwise we fail. " +
                            "burningManSelectionHeight=%s, expectedBurningManSelectionHeight=%s, diff=%s",
                    burningManSelectionHeight, expectedBurningManSelectionHeight, diff);

        }
        return burningManSelectionHeight;
    }


    /* --------------------------------------------------------------------- */
    // Delayed payout transaction input amount
    /* --------------------------------------------------------------------- */

    public static long checkDelayedPayoutTxInputAmount(long inputAmount, Trade trade) {
        checkIsPositive(inputAmount, "inputAmount");
        checkNotNull(trade, "trade must not be null");
        Offer offer = trade.getOffer();
        long tradeAmount = trade.getAmountAsLong();
        long buyerDeposit = offer.getBuyerSecurityDeposit().getValue();
        long sellerDeposit = offer.getSellerSecurityDeposit().getValue();
        long tradeTxFee = trade.getTradeTxFeeAsLong();
        long expectedAmount = tradeAmount +
                buyerDeposit +
                sellerDeposit +
                tradeTxFee;
        checkArgument(inputAmount == expectedAmount,
                "inputAmount must match expectedAmount. " +
                        "Trade amount: %s, buyer deposit: %s, seller deposit: %s, " +
                        "trade fee: %s, expected amount: %s",
                tradeAmount, buyerDeposit, sellerDeposit, tradeTxFee, expectedAmount);
        return inputAmount;
    }


    /* --------------------------------------------------------------------- */
    // Lock time
    /* --------------------------------------------------------------------- */

    public static long checkLockTime(long lockTime, boolean isAltcoin, BtcWalletService btcWalletService) {
        return checkLockTime(lockTime, isAltcoin, btcWalletService, Config.baseCurrencyNetwork().isMainnet());
    }

    @VisibleForTesting
    static long checkLockTime(long lockTime,
                              boolean isAltcoin,
                              BtcWalletService btcWalletService,
                              boolean isMainnet) {
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        checkIsPositive(lockTime, "lockTime");

        // For regtest dev testing we use shorter lock times and skip the test
        if (isMainnet) {
            int expectedUnlockHeight = btcWalletService.getBestChainHeight() + Restrictions.getLockTime(isAltcoin);
            // We allow a tolerance of 3 blocks as BestChainHeight might be a bit different on maker and taker in
            // case a new block was just found
            checkArgument(Math.abs(lockTime - expectedUnlockHeight) <= MAX_LOCKTIME_BLOCK_DEVIATION,
                    "Lock time of maker is more than 3 blocks different to the lockTime I " +
                            "calculated. Makers lockTime= " + lockTime + ", expectedUnlockHeight=" + expectedUnlockHeight);
        }
        return lockTime;
    }
}
