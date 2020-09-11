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
import bisq.core.dao.governance.param.Param;
import bisq.core.offer.Offer;
import bisq.core.support.dispute.Dispute;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import java.util.Set;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class DelayedPayoutTxValidation {

    public static class DonationAddressException extends Exception {
        DonationAddressException(String msg) {
            super(msg);
        }
    }

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

    public static void validateDonationAddress(String addressAsString, DaoFacade daoFacade)
            throws DonationAddressException {

        if (addressAsString == null) {
            return;
        }

        // We support any of the past addresses as well as in case the peer has not enabled the DAO or is out of sync we
        // do not want to break validation.
        Set<String> allPastParamValues = daoFacade.getAllPastParamValues(Param.RECIPIENT_BTC_ADDRESS);

        // If Dao is deactivated we need to add the default address as getAllPastParamValues will not return us any.
        allPastParamValues.add(Param.RECIPIENT_BTC_ADDRESS.getDefaultValue());

        // If Dao is deactivated we need to add the past addresses used as well.
        // This list need to be updated once a new address gets defined.
        allPastParamValues.add("3EtUWqsGThPtjwUczw27YCo6EWvQdaPUyp"); // burning man 2019
        allPastParamValues.add("3A8Zc1XioE2HRzYfbb5P8iemCS72M6vRJV"); // burningman2


        if (!allPastParamValues.contains(addressAsString)) {
            String errorMsg = "Donation address is not a valid DAO donation address." +
                    "\nAddress used in the dispute: " + addressAsString +
                    "\nAll DAO param donation addresses:" + allPastParamValues;
            log.error(errorMsg);
            throw new DonationAddressException(errorMsg);
        }
    }

    public static void validatePayoutTx(Trade trade,
                                        Transaction delayedPayoutTx,
                                        DaoFacade daoFacade,
                                        BtcWalletService btcWalletService)
            throws DonationAddressException, MissingDelayedPayoutTxException,
            InvalidTxException, InvalidLockTimeException, AmountMismatchException {
        validatePayoutTx(trade,
                delayedPayoutTx,
                null,
                daoFacade,
                btcWalletService,
                null);
    }

    public static void validatePayoutTx(Trade trade,
                                        Transaction delayedPayoutTx,
                                        @Nullable Dispute dispute,
                                        DaoFacade daoFacade,
                                        BtcWalletService btcWalletService)
            throws DonationAddressException, MissingDelayedPayoutTxException,
            InvalidTxException, InvalidLockTimeException, AmountMismatchException {
        validatePayoutTx(trade,
                delayedPayoutTx,
                dispute,
                daoFacade,
                btcWalletService,
                null);
    }

    public static void validatePayoutTx(Trade trade,
                                        Transaction delayedPayoutTx,
                                        DaoFacade daoFacade,
                                        BtcWalletService btcWalletService,
                                        @Nullable Consumer<String> addressConsumer)
            throws DonationAddressException, MissingDelayedPayoutTxException,
            InvalidTxException, InvalidLockTimeException, AmountMismatchException {
        validatePayoutTx(trade,
                delayedPayoutTx,
                null,
                daoFacade,
                btcWalletService,
                addressConsumer);
    }

    public static void validatePayoutTx(Trade trade,
                                        Transaction delayedPayoutTx,
                                        @Nullable Dispute dispute,
                                        DaoFacade daoFacade,
                                        BtcWalletService btcWalletService,
                                        @Nullable Consumer<String> addressConsumer)
            throws DonationAddressException, MissingDelayedPayoutTxException,
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
            errorMsg = "Output value of deposit tx and delayed payout tx is not matching. Output: " + output + " / msOutputAmount: " + msOutputAmount;
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new AmountMismatchException(errorMsg);
        }

        NetworkParameters params = btcWalletService.getParams();
        Address address = output.getAddressFromP2PKHScript(params);
        if (address == null) {
            // The donation address can be a multisig address as well.
            address = output.getAddressFromP2SH(params);
            if (address == null) {
                errorMsg = "Donation address cannot be resolved (not of type P2PKHScript or P2SH). Output: " + output;
                log.error(errorMsg);
                log.error(delayedPayoutTx.toString());
                throw new DonationAddressException(errorMsg);
            }
        }

        String addressAsString = address.toString();
        if (addressConsumer != null) {
            addressConsumer.accept(addressAsString);
        }

        validateDonationAddress(addressAsString, daoFacade);

        if (dispute != null) {
            // Verify that address in the dispute matches the one in the trade.
            String donationAddressOfDelayedPayoutTx = dispute.getDonationAddressOfDelayedPayoutTx();
            // Old clients don't have it set yet. Can be removed after a forced update
            if (donationAddressOfDelayedPayoutTx != null) {
                checkArgument(addressAsString.equals(donationAddressOfDelayedPayoutTx),
                        "donationAddressOfDelayedPayoutTx from dispute does not match address from delayed payout tx");
            }
        }
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
