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
import bisq.core.support.dispute.DisputeValidation;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.validation.exceptions.InvalidAmountException;
import bisq.core.trade.validation.exceptions.InvalidInputException;
import bisq.core.trade.validation.exceptions.InvalidLockTimeException;
import bisq.core.trade.validation.exceptions.InvalidTxException;
import bisq.core.trade.validation.exceptions.MissingTxException;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import com.google.common.annotations.VisibleForTesting;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.util.Validator.checkIsPositive;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
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

    public static void validateDelayedPayoutTx(Transaction delayedPayoutTx,
                                               Trade trade,
                                               BtcWalletService btcWalletService)
            throws DisputeValidation.AddressException, MissingTxException,
            InvalidTxException, InvalidLockTimeException, InvalidAmountException {
        validateDelayedPayoutTx(delayedPayoutTx,
                trade,
                btcWalletService,
                null);
    }

    public static void validateDelayedPayoutTx(Transaction delayedPayoutTx,
                                               Trade trade,
                                               BtcWalletService btcWalletService,
                                               @Nullable Consumer<String> addressConsumer)
            throws DisputeValidation.AddressException, MissingTxException,
            InvalidTxException, InvalidLockTimeException, InvalidAmountException {
        String errorMsg;
        if (delayedPayoutTx == null) {
            errorMsg = "DelayedPayoutTx must not be null";
            log.error(errorMsg);
            throw new MissingTxException("DelayedPayoutTx must not be null");
        }

        // Validate tx structure
        if (delayedPayoutTx.getInputs().size() != 1) {
            errorMsg = "Number of delayedPayoutTx inputs must be 1";
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new InvalidTxException(errorMsg);
        }


        // connectedOutput is null and input.getValue() is null at that point as the tx is not committed to the wallet
        // yet. So we cannot check that the input matches but we did the amount check earlier in the trade protocol.

        // Validate lock time
        if (delayedPayoutTx.getLockTime() != trade.getLockTime()) {
            errorMsg = "delayedPayoutTx.getLockTime() must match trade.getLockTime()";
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new InvalidLockTimeException(errorMsg);
        }

        // Validate seq num
        if (delayedPayoutTx.getInput(0).getSequenceNumber() != TransactionInput.NO_SEQUENCE - 1) {
            errorMsg = "Sequence number must be 0xFFFFFFFE";
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new InvalidLockTimeException(errorMsg);
        }

        if (trade.isUsingLegacyBurningMan()) {
            if (delayedPayoutTx.getOutputs().size() != 1) {
                errorMsg = "Number of delayedPayoutTx outputs must be 1";
                log.error(errorMsg);
                log.error(delayedPayoutTx.toString());
                throw new InvalidTxException(errorMsg);
            }

            // Check amount
            TransactionOutput output = delayedPayoutTx.getOutput(0);
            Offer offer = checkNotNull(trade.getOffer());
            Coin msOutputAmount = offer.getBuyerSecurityDeposit()
                    .add(offer.getSellerSecurityDeposit())
                    .add(checkNotNull(trade.getAmount()));

            if (!output.getValue().equals(msOutputAmount)) {
                errorMsg = "Output value of deposit tx and delayed payout tx is not matching. Output: " + output + " / msOutputAmount: " + msOutputAmount;
                log.error(errorMsg);
                log.error(delayedPayoutTx.toString());
                throw new InvalidAmountException(errorMsg);
            }

            NetworkParameters params = btcWalletService.getParams();
            if (addressConsumer != null) {
                String delayedPayoutTxOutputAddress = output.getScriptPubKey().getToAddress(params).toString();
                addressConsumer.accept(delayedPayoutTxOutputAddress);
            }
        }
    }

    public static void validateDelayedPayoutTxInput(Transaction delayedPayoutTx, Transaction depositTx)
            throws InvalidInputException {
        TransactionInput input = delayedPayoutTx.getInput(0);
        checkNotNull(input, "delayedPayoutTx.getInput(0) must not be null");
        // input.getConnectedOutput() is null as the tx is not committed at that point

        TransactionOutPoint outpoint = input.getOutpoint();
        if (!outpoint.getHash().toString().equals(depositTx.getTxId().toString()) || outpoint.getIndex() != 0) {
            throw new InvalidInputException("Input of delayed payout transaction does not point to output of deposit tx.\n" +
                    "Delayed payout tx=" + delayedPayoutTx + "\n" +
                    "Deposit tx=" + depositTx);
        }
    }
}
