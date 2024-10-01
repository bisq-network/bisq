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

package bisq.core.support.dispute;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.support.SupportType;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.JsonUtil;
import bisq.core.util.validation.RegexValidatorFactory;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.util.Tuple5;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class DisputeValidation {
    public static void validateDisputeData(Dispute dispute,
                                           BtcWalletService btcWalletService) throws ValidationException {
        try {
            Contract contract = dispute.getContract();
            checkArgument(contract.getOfferPayload().getId().equals(dispute.getTradeId()), "Invalid tradeId");
            checkArgument(dispute.getContractAsJson().equals(JsonUtil.objectToJson(contract)), "Invalid contractAsJson");
            checkArgument(Arrays.equals(Objects.requireNonNull(dispute.getContractHash()), Hash.getSha256Hash(checkNotNull(dispute.getContractAsJson()))),
                    "Invalid contractHash");

            Optional<Transaction> depositTx = dispute.findDepositTx(btcWalletService);
            if (depositTx.isPresent()) {
                checkArgument(depositTx.get().getTxId().toString().equals(dispute.getDepositTxId()), "Invalid depositTxId");
                checkArgument(depositTx.get().getInputs().size() >= 2, "DepositTx must have at least 2 inputs");
            }

            try {
                // Only the dispute opener has set the signature
                String makerContractSignature = dispute.getMakerContractSignature();
                if (makerContractSignature != null) {
                    Sig.verify(contract.getMakerPubKeyRing().getSignaturePubKey(),
                            dispute.getContractAsJson(),
                            makerContractSignature);
                }
                String takerContractSignature = dispute.getTakerContractSignature();
                if (takerContractSignature != null) {
                    Sig.verify(contract.getTakerPubKeyRing().getSignaturePubKey(),
                            dispute.getContractAsJson(),
                            takerContractSignature);
                }
            } catch (CryptoException e) {
                throw new ValidationException(dispute, e.getMessage());
            }
        } catch (Throwable t) {
            throw new ValidationException(dispute, t.getMessage());
        }
    }

    public static void validateTradeAndDispute(Dispute dispute, Trade trade, BtcWalletService btcWalletService)
            throws ValidationException {
        try {
            checkArgument(dispute.getContract().equals(trade.getContract()),
                    "contract must match contract from trade");

            if (trade.hasV5Protocol()) {
                String buyersWarningTxId = toTxId(trade.getBuyersWarningTx(btcWalletService));
                String sellersWarningTxId = toTxId(trade.getSellersWarningTx(btcWalletService));
                String buyersRedirectTxId = toTxId(trade.getBuyersRedirectTx(btcWalletService));
                String sellersRedirectTxId = toTxId(trade.getSellersRedirectTx(btcWalletService));
                checkNotNull(dispute.getWarningTxId(), "warningTxId must not be null");
                checkArgument(Arrays.asList(buyersWarningTxId, sellersWarningTxId).contains(dispute.getWarningTxId()),
                        "warningTxId must match either buyer's or seller's warningTxId from trade");
                checkNotNull(dispute.getRedirectTxId(), "redirectTxId must not be null");
                checkArgument(Arrays.asList(buyersRedirectTxId, sellersRedirectTxId).contains(dispute.getRedirectTxId()),
                        "redirectTxId must match either buyer's or seller's redirectTxId from trade");
                boolean isBuyerWarning = dispute.getWarningTxId().equals(buyersWarningTxId);
                boolean isBuyerRedirect = dispute.getRedirectTxId().equals(buyersRedirectTxId);
                if (isBuyerWarning) {
                    checkArgument(!isBuyerRedirect, "buyer's redirectTx must be used with seller's warningTx");
                } else {
                    checkArgument(isBuyerRedirect, "seller's redirectTx must be used with buyer's warningTx");
                }
            } else {
                checkNotNull(trade.getDelayedPayoutTx(), "trade.getDelayedPayoutTx() must not be null");
                checkNotNull(dispute.getDelayedPayoutTxId(), "delayedPayoutTxId must not be null");
                checkArgument(dispute.getDelayedPayoutTxId().equals(trade.getDelayedPayoutTx().getTxId().toString()),
                        "delayedPayoutTxId must match delayedPayoutTxId from trade");
            }

            checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
            checkNotNull(dispute.getDepositTxId(), "depositTxId must not be null");
            checkArgument(dispute.getDepositTxId().equals(trade.getDepositTx().getTxId().toString()),
                    "depositTx must match depositTx from trade");

            checkNotNull(dispute.getDepositTxSerialized(), "depositTxSerialized must not be null");
        } catch (Throwable t) {
            throw new ValidationException(dispute, t.getMessage());
        }
    }

    @Nullable
    private static String toTxId(@Nullable Transaction tx) {
        return tx != null ? tx.getTxId().toString() : null;
    }


    public static void validateSenderNodeAddress(Dispute dispute,
                                                 NodeAddress senderNodeAddress) throws NodeAddressException {
        if (!senderNodeAddress.equals(dispute.getContract().getBuyerNodeAddress())
                && !senderNodeAddress.equals(dispute.getContract().getSellerNodeAddress())) {
            throw new NodeAddressException(dispute, "senderNodeAddress not matching any of the traders node addresses");
        }
    }

    public static void validateNodeAddresses(Dispute dispute, Config config)
            throws NodeAddressException {
        if (!config.useLocalhostForP2P) {
            validateNodeAddress(dispute, dispute.getContract().getBuyerNodeAddress());
            validateNodeAddress(dispute, dispute.getContract().getSellerNodeAddress());
        }
    }

    private static void validateNodeAddress(Dispute dispute, NodeAddress nodeAddress) throws NodeAddressException {
        if (!RegexValidatorFactory.onionAddressRegexValidator().validate(nodeAddress.getFullAddress()).isValid) {
            String msg = "Node address " + nodeAddress.getFullAddress() + " at dispute with trade ID " +
                    dispute.getShortTradeId() + " is not a valid address";
            log.error(msg);
            throw new NodeAddressException(dispute, msg);
        }
    }

    public static void validateDonationAddressMatchesAnyPastParamValues(Dispute dispute,
                                                                        String addressAsString,
                                                                        DaoFacade daoFacade)
            throws AddressException {
        Set<String> allPastParamValues = daoFacade.getAllDonationAddresses();
        if (!allPastParamValues.contains(addressAsString)) {
            String errorMsg = "Donation address is not a valid DAO donation address." +
                    "\nAddress used in the dispute: " + addressAsString +
                    "\nAll DAO param donation addresses:" + allPastParamValues;
            log.error(errorMsg);
            throw new AddressException(dispute, errorMsg);
        }
    }

    public static void validateDonationAddress(Dispute dispute,
                                               Transaction delayedPayoutTx,
                                               NetworkParameters params)
            throws AddressException {
        TransactionOutput output = delayedPayoutTx.getOutput(0);
        Address address = output.getScriptPubKey().getToAddress(params);
        if (address == null) {
            String errorMsg = "Donation address cannot be resolved (not of type P2PK nor P2SH nor P2WH). Output: " + output;
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new DisputeValidation.AddressException(dispute, errorMsg);
        }

        // Verify that address in the dispute matches the one in the trade.
        String delayedPayoutTxOutputAddress = address.toString();
        checkArgument(delayedPayoutTxOutputAddress.equals(dispute.getDonationAddressOfDelayedPayoutTx()),
                "donationAddressOfDelayedPayoutTx from dispute does not match address from delayed payout tx. " +
                        "delayedPayoutTxOutputAddress=" + delayedPayoutTxOutputAddress +
                        "; dispute.getDonationAddressOfDelayedPayoutTx()=" + dispute.getDonationAddressOfDelayedPayoutTx());
    }

    // TODO: Refactor:
    public static void testIfAnyDisputeTriedReplay(List<Dispute> disputeList,
                                                   Consumer<DisputeReplayException> exceptionHandler) {
        var tuple = getTestReplayHashMaps(disputeList);
        Map<String, Set<String>> disputesPerTradeId = tuple.first;
        Map<String, Set<String>> disputesPerDelayedPayoutTxId = tuple.second;
        Map<String, Set<String>> disputesPerWarningId = tuple.third;
        Map<String, Set<String>> disputesPerRedirectTxId = tuple.fourth;
        Map<String, Set<String>> disputesPerDepositTxId = tuple.fifth;

        disputeList.forEach(disputeToTest -> {
            try {
                testIfDisputeTriesReplay(disputeToTest,
                        disputesPerTradeId,
                        disputesPerDelayedPayoutTxId,
                        disputesPerWarningId,
                        disputesPerRedirectTxId,
                        disputesPerDepositTxId);

            } catch (DisputeReplayException e) {
                exceptionHandler.accept(e);
            }
        });
    }

    // TODO: Refactor:
    public static void testIfDisputeTriesReplay(Dispute dispute,
                                                List<Dispute> disputeList) throws DisputeReplayException {
        var tuple = getTestReplayHashMaps(disputeList);
        Map<String, Set<String>> disputesPerTradeId = tuple.first;
        Map<String, Set<String>> disputesPerDelayedPayoutTxId = tuple.second;
        Map<String, Set<String>> disputesPerWarningTxId = tuple.third;
        Map<String, Set<String>> disputesPerRedirectTxId = tuple.fourth;
        Map<String, Set<String>> disputesPerDepositTxId = tuple.fifth;

        testIfDisputeTriesReplay(dispute,
                disputesPerTradeId,
                disputesPerDelayedPayoutTxId,
                disputesPerWarningTxId,
                disputesPerRedirectTxId,
                disputesPerDepositTxId);
    }

    // TODO: Refactor:
    private static Tuple5<Map<String, Set<String>>, Map<String, Set<String>>, Map<String, Set<String>>, Map<String, Set<String>>, Map<String, Set<String>>> getTestReplayHashMaps(
            List<Dispute> disputeList) {
        Map<String, Set<String>> disputesPerTradeId = new HashMap<>();
        Map<String, Set<String>> disputesPerDelayedPayoutTxId = new HashMap<>();
        Map<String, Set<String>> disputesPerWarningTxId = new HashMap<>();
        Map<String, Set<String>> disputesPerRedirectTxId = new HashMap<>();
        Map<String, Set<String>> disputesPerDepositTxId = new HashMap<>();
        disputeList.forEach(dispute -> {
            String uid = dispute.getUid();

            String tradeId = dispute.getTradeId();
            disputesPerTradeId.computeIfAbsent(tradeId, id -> new HashSet<>()).add(uid);

            String delayedPayoutTxId = dispute.getDelayedPayoutTxId();
            if (delayedPayoutTxId != null) {
                disputesPerDelayedPayoutTxId.computeIfAbsent(delayedPayoutTxId, id -> new HashSet<>()).add(uid);
            }
            String warningTxId = dispute.getWarningTxId();
            if (warningTxId != null) {
                disputesPerWarningTxId.computeIfAbsent(warningTxId, id -> new HashSet<>()).add(uid);
            }
            String redirectTxId = dispute.getRedirectTxId();
            if (redirectTxId != null) {
                disputesPerRedirectTxId.computeIfAbsent(redirectTxId, id -> new HashSet<>()).add(uid);
            }
            String depositTxId = dispute.getDepositTxId();
            if (depositTxId != null) {
                disputesPerDepositTxId.computeIfAbsent(depositTxId, id -> new HashSet<>()).add(uid);
            }
        });

        return new Tuple5<>(disputesPerTradeId, disputesPerDelayedPayoutTxId, disputesPerWarningTxId,
                disputesPerRedirectTxId, disputesPerDepositTxId);
    }

    // TODO: Refactor:
    private static void testIfDisputeTriesReplay(Dispute disputeToTest,
                                                 Map<String, Set<String>> disputesPerTradeId,
                                                 Map<String, Set<String>> disputesPerDelayedPayoutTxId,
                                                 Map<String, Set<String>> disputesPerWarningTxId,
                                                 Map<String, Set<String>> disputesPerRedirectTxId,
                                                 Map<String, Set<String>> disputesPerDepositTxId)
            throws DisputeReplayException {
        try {
            String disputeToTestTradeId = disputeToTest.getTradeId();
            String disputeToTestDelayedPayoutTxId = disputeToTest.getDelayedPayoutTxId();
            String disputeToTestWarningTxId = disputeToTest.getWarningTxId();
            String disputeToTestRedirectTxId = disputeToTest.getRedirectTxId();
            String disputeToTestDepositTxId = disputeToTest.getDepositTxId();
            String disputeToTestUid = disputeToTest.getUid();

            // For pre v1.4.0 we do not get the delayed payout tx sent in mediation cases but in refund agent case we do.
            // So until all users have updated to 1.4.0 we only check in refund agent case. With 1.4.0 we send the
            // delayed payout tx also in mediation cases and that if check can be removed.
            if (disputeToTest.getSupportType() == SupportType.REFUND) {
                if (disputeToTestWarningTxId == null) {
                    checkNotNull(disputeToTestDelayedPayoutTxId,
                            "Delayed payout transaction ID is null. " +
                                    "Trade ID: %s", disputeToTestTradeId);
                } else {
                    checkNotNull(disputeToTestRedirectTxId,
                            "Redirect transaction ID is null. " +
                                    "Trade ID: %s", disputeToTestTradeId);
                }
            }
            checkNotNull(disputeToTestDepositTxId,
                    "depositTxId must not be null. Trade ID: %s", disputeToTestTradeId);
            checkNotNull(disputeToTestUid,
                    "agentsUid must not be null. Trade ID: %s", disputeToTestTradeId);

            Set<String> disputesPerTradeIdItems = disputesPerTradeId.get(disputeToTestTradeId);
            checkArgument(disputesPerTradeIdItems == null || disputesPerTradeIdItems.size() <= 2,
                    "We found more than 2 disputes with the same trade ID. " +
                            "Trade ID: %s", disputeToTestTradeId);
            Set<String> disputesPerDelayedPayoutTxIdItems = disputesPerDelayedPayoutTxId.get(disputeToTestDelayedPayoutTxId);
            checkArgument(disputesPerDelayedPayoutTxIdItems == null || disputesPerDelayedPayoutTxIdItems.size() <= 2,
                    "We found more than 2 disputes with the same delayedPayoutTxId. " +
                            "Trade ID: %s", disputeToTestTradeId);
            Set<String> disputesPerWarningTxIdItems = disputesPerWarningTxId.get(disputeToTestWarningTxId);
            checkArgument(disputesPerWarningTxIdItems == null || disputesPerWarningTxIdItems.size() <= 2,
                    "We found more than 2 disputes with the same warningTxId. " +
                            "Trade ID: %s", disputeToTestTradeId);
            Set<String> disputesPerRedirectTxIdItems = disputesPerRedirectTxId.get(disputeToTestRedirectTxId);
            checkArgument(disputesPerRedirectTxIdItems == null || disputesPerRedirectTxIdItems.size() <= 2,
                    "We found more than 2 disputes with the same redirectTxId. " +
                            "Trade ID: %s", disputeToTestTradeId);
            Set<String> disputesPerDepositTxIdItems = disputesPerDepositTxId.get(disputeToTestDepositTxId);
            checkArgument(disputesPerDepositTxIdItems == null || disputesPerDepositTxIdItems.size() <= 2,
                    "We found more than 2 disputes with the same depositTxId. " +
                            "Trade ID: %s", disputeToTestTradeId);
        } catch (IllegalArgumentException e) {
            throw new DisputeReplayException(disputeToTest, e.getMessage());
        } catch (NullPointerException e) {
            log.error("NullPointerException at testIfDisputeTriesReplay: " +
                            "disputeToTest={}, disputesPerTradeId={}, disputesPerDelayedPayoutTxId={}, " +
                            "disputesPerWarningTxId={}, disputesPerRedirectTxId={}, " +
                            "disputesPerDepositTxId={}",
                    disputeToTest, disputesPerTradeId, disputesPerDelayedPayoutTxId, disputesPerWarningTxId,
                    disputesPerRedirectTxId, disputesPerDepositTxId);
            throw new DisputeReplayException(disputeToTest, e + " at dispute " + disputeToTest);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Exceptions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static class ValidationException extends Exception {
        @Getter
        private final Dispute dispute;

        ValidationException(Dispute dispute, String msg) {
            super(msg);
            this.dispute = dispute;
        }
    }

    public static class NodeAddressException extends ValidationException {
        NodeAddressException(Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }


    public static class AddressException extends ValidationException {
        AddressException(Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }

    public static class DisputeReplayException extends ValidationException {
        DisputeReplayException(Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }
}
