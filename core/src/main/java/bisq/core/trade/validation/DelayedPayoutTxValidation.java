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

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import com.google.common.annotations.VisibleForTesting;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import static bisq.core.trade.validation.TransactionValidation.checkTransaction;
import static bisq.core.util.Validator.checkIsPositive;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class DelayedPayoutTxValidation {
    public static final int MAX_LOCKTIME_BLOCK_DEVIATION = 3; // Peers latest block height must inside a +/- 3 blocks tolerance to ours.

    private DelayedPayoutTxValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Delayed payout transaction
    /* --------------------------------------------------------------------- */

    public static Transaction checkDelayedPayoutTx(Transaction delayedPayoutTx,
                                                   Trade trade,
                                                   BtcWalletService btcWalletService) {
        return checkDelayedPayoutTx(delayedPayoutTx,
                trade,
                btcWalletService,
                null);
    }

    public static Transaction checkDelayedPayoutTx(Transaction delayedPayoutTx,
                                                   Trade trade,
                                                   BtcWalletService btcWalletService,
                                                   @Nullable Consumer<String> addressConsumer) {
        Transaction checkedDelayedPayoutTx = checkTransaction(checkNotNull(delayedPayoutTx,
                "delayedPayoutTx must not be null"));
        Trade checkedTrade = checkNotNull(trade, "trade must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        checkArgument(checkedDelayedPayoutTx.getInputs().size() == 1,
                "Number of delayedPayoutTx inputs must be 1");
        checkArgument(checkedDelayedPayoutTx.getLockTime() == checkedTrade.getLockTime(),
                "delayedPayoutTx.getLockTime() must match trade.getLockTime()");
        checkArgument(checkedDelayedPayoutTx.getInput(0).getSequenceNumber() == TransactionInput.NO_SEQUENCE - 1,
                "Sequence number must be 0xFFFFFFFE");

        if (checkedTrade.isUsingLegacyBurningMan()) {
            checkLegacyDelayedPayoutTxOutput(checkedDelayedPayoutTx,
                    checkedTrade,
                    btcWalletService,
                    addressConsumer);
        }

        return checkedDelayedPayoutTx;
    }

    private static void checkLegacyDelayedPayoutTxOutput(Transaction delayedPayoutTx,
                                                         Trade trade,
                                                         BtcWalletService btcWalletService,
                                                         @Nullable Consumer<String> addressConsumer) {
        checkArgument(delayedPayoutTx.getOutputs().size() == 1,
                "Number of delayedPayoutTx outputs must be 1");

        TransactionOutput output = delayedPayoutTx.getOutput(0);
        Offer offer = checkNotNull(trade.getOffer(), "trade.getOffer() must not be null");
        Coin buyerSecurityDeposit = checkIsPositive(offer.getBuyerSecurityDeposit(),
                "offer.getBuyerSecurityDeposit()");
        Coin sellerSecurityDeposit = checkIsPositive(offer.getSellerSecurityDeposit(),
                "offer.getSellerSecurityDeposit()");
        Coin tradeAmount = checkIsPositive(trade.getAmount(), "trade.getAmount()");
        Coin msOutputAmount = buyerSecurityDeposit
                .add(sellerSecurityDeposit)
                .add(tradeAmount);

        checkArgument(output.getValue().equals(msOutputAmount),
                "Output value of deposit tx and delayed payout tx is not matching. " +
                        "Output: %s / msOutputAmount: %s",
                output,
                msOutputAmount);

        if (addressConsumer != null) {
            NetworkParameters params = checkNotNull(btcWalletService.getParams(),
                    "btcWalletService.getParams() must not be null");
            String delayedPayoutTxOutputAddress = output.getScriptPubKey().getToAddress(params).toString();
            addressConsumer.accept(delayedPayoutTxOutputAddress);
        }
    }

    public static Transaction checkDelayedPayoutTxInput(Transaction delayedPayoutTx,
                                                        Transaction depositTx) {
        Transaction checkedDelayedPayoutTx = checkNotNull(delayedPayoutTx,
                "delayedPayoutTx must not be null");
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        checkArgument(checkedDelayedPayoutTx.getInputs().size() == 1,
                "Number of delayedPayoutTx inputs must be 1");

        TransactionInput input = checkedDelayedPayoutTx.getInput(0);
        TransactionOutPoint outpoint = checkNotNull(input.getOutpoint(),
                "delayedPayoutTx input outpoint must not be null");
        checkArgument(checkedDepositTx.getTxId().equals(outpoint.getHash()) && outpoint.getIndex() == 0,
                "Input of delayed payout transaction does not point to output 0 of deposit tx. " +
                        "delayedPayoutTxId=%s, depositTxId=%s, outpointHash=%s, outpointIndex=%s",
                checkedDelayedPayoutTx.getTxId(),
                checkedDepositTx.getTxId(),
                outpoint.getHash(),
                outpoint.getIndex());
        return checkedDelayedPayoutTx;
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
        Trade checkedTrade = checkNotNull(trade, "trade must not be null");
        Coin tradeTxFee = Coin.valueOf(checkIsPositive(checkedTrade.getTradeTxFeeAsLong(), "tradeTxFee"));
        return checkDelayedPayoutTxInputAmount(inputAmount, checkedTrade, tradeTxFee);
    }

    public static long checkDelayedPayoutTxInputAmount(long inputAmount, Trade trade, long tradeTxFee) {
        return checkDelayedPayoutTxInputAmount(inputAmount,
                trade,
                Coin.valueOf(checkIsPositive(tradeTxFee, "tradeTxFee")));
    }

    public static long checkDelayedPayoutTxInputAmount(long inputAmount, Trade trade, Coin tradeTxFee) {
        checkIsPositive(inputAmount, "inputAmount");
        Trade checkedTrade = checkNotNull(trade, "trade must not be null");
        Coin checkedInputAmount = Coin.valueOf(inputAmount);
        Coin checkedTradeTxFee = checkIsPositive(tradeTxFee, "tradeTxFee");

        Offer offer = checkNotNull(checkedTrade.getOffer(), "trade.getOffer() must not be null");
        Coin tradeAmount = Coin.valueOf(checkIsPositive(checkedTrade.getAmountAsLong(), "tradeAmount"));
        Coin buyerDeposit = checkIsPositive(offer.getBuyerSecurityDeposit(),
                "offer.getBuyerSecurityDeposit()");
        Coin sellerDeposit = checkIsPositive(offer.getSellerSecurityDeposit(),
                "offer.getSellerSecurityDeposit()");
        Coin expectedAmount = tradeAmount
                .add(buyerDeposit)
                .add(sellerDeposit)
                .add(checkedTradeTxFee);
        checkArgument(checkedInputAmount.equals(expectedAmount),
                "inputAmount must match expectedAmount. " +
                        "Trade amount: %s, buyer deposit: %s, seller deposit: %s, " +
                        "trade fee: %s, expected amount: %s",
                tradeAmount.toFriendlyString(), buyerDeposit.toFriendlyString(), sellerDeposit.toFriendlyString(),
                checkedTradeTxFee.toFriendlyString(), expectedAmount.toFriendlyString());
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
