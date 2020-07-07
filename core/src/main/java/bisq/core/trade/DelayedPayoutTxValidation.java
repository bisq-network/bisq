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

package bisq.core.trade;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.offer.Offer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class DelayedPayoutTxValidation {

    public static class MissingDelayedPayoutTxException extends Exception {
        MissingDelayedPayoutTxException(String msg) {
            super(msg);
        }
    }

    public static class InvalidTxException extends Exception {
        InvalidTxException(String msg) {
            super(msg);
        }
    }

    public static class AmountMismatchException extends Exception {
        AmountMismatchException(String msg) {
            super(msg);
        }
    }

    public static class InvalidLockTimeException extends Exception {
        InvalidLockTimeException(String msg) {
            super(msg);
        }
    }

    public static class InvalidInputException extends Exception {
        InvalidInputException(String msg) {
            super(msg);
        }
    }

    public static void validatePayoutTx(Trade trade,
                                        Transaction delayedPayoutTx,
                                        DaoFacade daoFacade,
                                        BtcWalletService btcWalletService)
            throws DonationAddressValidation.DonationAddressException, MissingDelayedPayoutTxException,
            InvalidTxException, InvalidLockTimeException, AmountMismatchException {
        String errorMsg;
        if (delayedPayoutTx == null) {
            errorMsg = "DelayedPayoutTx must not be null";
            log.error(errorMsg);
            throw new MissingDelayedPayoutTxException("DelayedPayoutTx must not be null");
        }

        // Validate tx structure
        if (delayedPayoutTx.getInputs().size() != 1) {
            errorMsg = "Number of delayedPayoutTx inputs must be 1";
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new InvalidTxException(errorMsg);
        }

        if (delayedPayoutTx.getOutputs().size() != 1) {
            errorMsg = "Number of delayedPayoutTx outputs must be 1";
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

        // Check amount
        TransactionOutput output = delayedPayoutTx.getOutput(0);
        Offer offer = checkNotNull(trade.getOffer());
        Coin msOutputAmount = offer.getBuyerSecurityDeposit()
                .add(offer.getSellerSecurityDeposit())
                .add(checkNotNull(trade.getTradeAmount()));

        if (!output.getValue().equals(msOutputAmount)) {
            errorMsg = "Output value of deposit tx and delayed payout tx is not matching. Output: " + output +
                    " / msOutputAmount: " + msOutputAmount;
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new AmountMismatchException(errorMsg);
        }


        // Validate donation address
        // Get most recent donation address.
        // We do not support past DAO param addresses to avoid that those receive funds (no bond set up anymore).
        // Users who have not synced the DAO cannot trade.

        DonationAddressValidation.validateDonationAddress(output, delayedPayoutTx, daoFacade, btcWalletService);
    }

    public static void validatePayoutTxInput(Transaction depositTx,
                                             Transaction delayedPayoutTx)
            throws InvalidInputException {
        TransactionInput input = delayedPayoutTx.getInput(0);
        checkNotNull(input, "delayedPayoutTx.getInput(0) must not be null");
        // input.getConnectedOutput() is null as the tx is not committed at that point

        TransactionOutPoint outpoint = input.getOutpoint();
        if (!outpoint.getHash().toString().equals(depositTx.getHashAsString()) || outpoint.getIndex() != 0) {
            throw new InvalidInputException("Input of delayed payout transaction does not point to output of deposit tx.\n" +
                    "Delayed payout tx=" + delayedPayoutTx + "\n" +
                    "Deposit tx=" + depositTx);
        }
    }
}
