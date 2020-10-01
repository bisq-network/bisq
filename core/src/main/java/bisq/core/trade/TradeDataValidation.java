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
import bisq.core.support.dispute.Dispute;
import bisq.core.util.validation.RegexValidatorFactory;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TradeDataValidation {

    public static void validateDonationAddress(String addressAsString, DaoFacade daoFacade)
            throws AddressException {
        validateDonationAddress(null, addressAsString, daoFacade);
    }

    public static void validateNodeAddress(Dispute dispute, NodeAddress nodeAddress, Config config)
            throws NodeAddressException {
        if (!config.useLocalhostForP2P && !RegexValidatorFactory.onionAddressRegexValidator().validate(nodeAddress.getFullAddress()).isValid) {
            String msg = "Node address " + nodeAddress.getFullAddress() + " at dispute with trade ID " +
                    dispute.getShortTradeId() + " is not a valid address";
            log.error(msg);
            throw new NodeAddressException(dispute, msg);
        }
    }

    public static void validateDonationAddress(@Nullable Dispute dispute, String addressAsString, DaoFacade daoFacade)
            throws AddressException {

        if (addressAsString == null) {
            log.debug("address is null at validateDonationAddress. This is expected in case of an not updated trader.");
            return;
        }

        Set<String> allPastParamValues = daoFacade.getAllDonationAddresses();
        if (!allPastParamValues.contains(addressAsString)) {
            String errorMsg = "Donation address is not a valid DAO donation address." +
                    "\nAddress used in the dispute: " + addressAsString +
                    "\nAll DAO param donation addresses:" + allPastParamValues;
            log.error(errorMsg);
            throw new AddressException(dispute, errorMsg);
        }
    }

    public static void testIfAnyDisputeTriedReplay(List<Dispute> disputeList,
                                                   Consumer<DisputeReplayException> exceptionHandler) {
        var tuple = getTestReplayHashMaps(disputeList);
        Map<String, Set<String>> disputesPerTradeId = tuple.first;
        Map<String, Set<String>> disputesPerDelayedPayoutTxId = tuple.second;
        Map<String, Set<String>> disputesPerDepositTxId = tuple.third;

        disputeList.forEach(disputeToTest -> {
            try {
                testIfDisputeTriesReplay(disputeToTest,
                        disputesPerTradeId,
                        disputesPerDelayedPayoutTxId,
                        disputesPerDepositTxId);

            } catch (DisputeReplayException e) {
                exceptionHandler.accept(e);
            }
        });
    }


    public static void testIfDisputeTriesReplay(Dispute dispute,
                                                List<Dispute> disputeList) throws DisputeReplayException {
        var tuple = TradeDataValidation.getTestReplayHashMaps(disputeList);
        Map<String, Set<String>> disputesPerTradeId = tuple.first;
        Map<String, Set<String>> disputesPerDelayedPayoutTxId = tuple.second;
        Map<String, Set<String>> disputesPerDepositTxId = tuple.third;

        testIfDisputeTriesReplay(dispute,
                disputesPerTradeId,
                disputesPerDelayedPayoutTxId,
                disputesPerDepositTxId);
    }


    private static Tuple3<Map<String, Set<String>>, Map<String, Set<String>>, Map<String, Set<String>>> getTestReplayHashMaps(
            List<Dispute> disputeList) {
        Map<String, Set<String>> disputesPerTradeId = new HashMap<>();
        Map<String, Set<String>> disputesPerDelayedPayoutTxId = new HashMap<>();
        Map<String, Set<String>> disputesPerDepositTxId = new HashMap<>();
        disputeList.forEach(dispute -> {
            String uid = dispute.getUid();

            String tradeId = dispute.getTradeId();
            disputesPerTradeId.putIfAbsent(tradeId, new HashSet<>());
            Set<String> set = disputesPerTradeId.get(tradeId);
            set.add(uid);

            String delayedPayoutTxId = dispute.getDelayedPayoutTxId();
            disputesPerDelayedPayoutTxId.putIfAbsent(delayedPayoutTxId, new HashSet<>());
            set = disputesPerDelayedPayoutTxId.get(delayedPayoutTxId);
            set.add(uid);

            String depositTxId = dispute.getDepositTxId();
            disputesPerDepositTxId.putIfAbsent(depositTxId, new HashSet<>());
            set = disputesPerDepositTxId.get(depositTxId);
            set.add(uid);
        });

        return new Tuple3<>(disputesPerTradeId, disputesPerDelayedPayoutTxId, disputesPerDepositTxId);
    }

    private static void testIfDisputeTriesReplay(Dispute disputeToTest,
                                                 Map<String, Set<String>> disputesPerTradeId,
                                                 Map<String, Set<String>> disputesPerDelayedPayoutTxId,
                                                 Map<String, Set<String>> disputesPerDepositTxId)
            throws DisputeReplayException {

        try {
            String disputeToTestTradeId = disputeToTest.getTradeId();
            String disputeToTestDelayedPayoutTxId = disputeToTest.getDelayedPayoutTxId();
            String disputeToTestDepositTxId = disputeToTest.getDepositTxId();
            String disputeToTestUid = disputeToTest.getUid();

            checkNotNull(disputeToTestDelayedPayoutTxId,
                    "delayedPayoutTxId must not be null. Trade ID: " + disputeToTestTradeId);
            checkNotNull(disputeToTestDepositTxId,
                    "depositTxId must not be null. Trade ID: " + disputeToTestTradeId);
            checkNotNull(disputeToTestUid,
                    "agentsUid must not be null. Trade ID: " + disputeToTestTradeId);

            checkArgument(disputesPerTradeId.get(disputeToTestTradeId).size() <= 2,
                    "We found more then 2 disputes with the same trade ID. " +
                            "Trade ID: " + disputeToTestTradeId);
            checkArgument(disputesPerDelayedPayoutTxId.get(disputeToTestDelayedPayoutTxId).size() <= 2,
                    "We found more then 2 disputes with the same delayedPayoutTxId. " +
                            "Trade ID: " + disputeToTestTradeId);
            checkArgument(disputesPerDepositTxId.get(disputeToTestDepositTxId).size() <= 2,
                    "We found more then 2 disputes with the same depositTxId. " +
                            "Trade ID: " + disputeToTestTradeId);

        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DisputeReplayException(disputeToTest, e.getMessage());
        }
    }

    public static void validateDelayedPayoutTx(Trade trade,
                                               Transaction delayedPayoutTx,
                                               DaoFacade daoFacade,
                                               BtcWalletService btcWalletService)
            throws AddressException, MissingTxException,
            InvalidTxException, InvalidLockTimeException, InvalidAmountException {
        validateDelayedPayoutTx(trade,
                delayedPayoutTx,
                null,
                daoFacade,
                btcWalletService,
                null);
    }

    public static void validateDelayedPayoutTx(Trade trade,
                                               Transaction delayedPayoutTx,
                                               @Nullable Dispute dispute,
                                               DaoFacade daoFacade,
                                               BtcWalletService btcWalletService)
            throws AddressException, MissingTxException,
            InvalidTxException, InvalidLockTimeException, InvalidAmountException {
        validateDelayedPayoutTx(trade,
                delayedPayoutTx,
                dispute,
                daoFacade,
                btcWalletService,
                null);
    }

    public static void validateDelayedPayoutTx(Trade trade,
                                               Transaction delayedPayoutTx,
                                               DaoFacade daoFacade,
                                               BtcWalletService btcWalletService,
                                               @Nullable Consumer<String> addressConsumer)
            throws AddressException, MissingTxException,
            InvalidTxException, InvalidLockTimeException, InvalidAmountException {
        validateDelayedPayoutTx(trade,
                delayedPayoutTx,
                null,
                daoFacade,
                btcWalletService,
                addressConsumer);
    }

    public static void validateDelayedPayoutTx(Trade trade,
                                               Transaction delayedPayoutTx,
                                               @Nullable Dispute dispute,
                                               DaoFacade daoFacade,
                                               BtcWalletService btcWalletService,
                                               @Nullable Consumer<String> addressConsumer)
            throws AddressException, MissingTxException,
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
            throw new InvalidAmountException(errorMsg);
        }

        NetworkParameters params = btcWalletService.getParams();
        Address address = output.getScriptPubKey().getToAddress(params);
        if (address == null) {
            errorMsg = "Donation address cannot be resolved (not of type P2PK nor P2SH nor P2WH). Output: " + output;
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new AddressException(dispute, errorMsg);
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
        if (!outpoint.getHash().toString().equals(depositTx.getTxId().toString()) || outpoint.getIndex() != 0) {
            throw new InvalidInputException("Input of delayed payout transaction does not point to output of deposit tx.\n" +
                    "Delayed payout tx=" + delayedPayoutTx + "\n" +
                    "Deposit tx=" + depositTx);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Exceptions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static class ValidationException extends Exception {
        @Nullable
        @Getter
        private final Dispute dispute;

        ValidationException(String msg) {
            this(null, msg);
        }

        ValidationException(@Nullable Dispute dispute, String msg) {
            super(msg);
            this.dispute = dispute;
        }
    }

    public static class AddressException extends ValidationException {
        AddressException(@Nullable Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }

    public static class MissingTxException extends ValidationException {
        MissingTxException(String msg) {
            super(msg);
        }
    }

    public static class InvalidTxException extends ValidationException {
        InvalidTxException(String msg) {
            super(msg);
        }
    }

    public static class InvalidAmountException extends ValidationException {
        InvalidAmountException(String msg) {
            super(msg);
        }
    }

    public static class InvalidLockTimeException extends ValidationException {
        InvalidLockTimeException(String msg) {
            super(msg);
        }
    }

    public static class InvalidInputException extends ValidationException {
        InvalidInputException(String msg) {
            super(msg);
        }
    }

    public static class DisputeReplayException extends ValidationException {
        DisputeReplayException(Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }

    public static class NodeAddressException extends ValidationException {
        NodeAddressException(Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }
}
