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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import com.google.common.base.CaseFormat;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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

    public static void testIfAnyDisputeTriedReplay(List<Dispute> disputeList,
                                                   Consumer<DisputeReplayException> exceptionHandler) {
        var map = getTestReplayMultimaps(disputeList);
        disputeList.forEach(disputeToTest -> {
            try {
                testIfDisputeTriesReplay(disputeToTest, map);
            } catch (DisputeReplayException e) {
                exceptionHandler.accept(e);
            }
        });
    }

    public static void testIfDisputeTriesReplay(Dispute dispute,
                                                List<Dispute> disputeList) throws DisputeReplayException {
        testIfDisputeTriesReplay(dispute, getTestReplayMultimaps(disputeList));
    }

    private static Map<DisputeIdField, SetMultimap<String, String>> getTestReplayMultimaps(List<Dispute> disputeList) {
        Map<DisputeIdField, SetMultimap<String, String>> disputesPerIdMap = new EnumMap<>(DisputeIdField.class);
        disputeList.forEach(dispute -> {
            String disputeUid = dispute.getUid();

            for (var field : DisputeIdField.values()) {
                String id = field.apply(dispute);
                if (id != null) {
                    disputesPerIdMap.computeIfAbsent(field, k -> HashMultimap.create()).put(id, disputeUid);
                }
            }
        });

        return disputesPerIdMap;
    }

    private static void testIfDisputeTriesReplay(Dispute disputeToTest,
                                                 Map<DisputeIdField, SetMultimap<String, String>> disputesPerIdMap)
            throws DisputeReplayException {
        try {
            String disputeToTestTradeId = disputeToTest.getTradeId();

            // For pre v1.4.0 we do not get the delayed payout tx sent in mediation cases but in refund agent case we do.
            // With 1.4.0 we send the delayed payout tx also in mediation cases. For v5 protocol trades, there is no DPT
            // and it is unknown which staged txs will be published, if any, so they are only sent in refund agent cases.
            if (disputeToTest.getSupportType() == SupportType.REFUND) {
                if (disputeToTest.getWarningTxId() == null) {
                    checkNotNull(disputeToTest.getDelayedPayoutTxId(),
                            "Delayed payout transaction ID is null. " +
                                    "Trade ID: %s", disputeToTestTradeId);
                } else {
                    checkNotNull(disputeToTest.getRedirectTxId(),
                            "Redirect transaction ID is null. " +
                                    "Trade ID: %s", disputeToTestTradeId);
                }
            }
            checkNotNull(disputeToTest.getDepositTxId(),
                    "depositTxId must not be null. Trade ID: %s", disputeToTestTradeId);
            checkNotNull(disputeToTest.getUid(),
                    "agentsUid must not be null. Trade ID: %s", disputeToTestTradeId);

            for (DisputeIdField field : disputesPerIdMap.keySet()) {
                String id = field.apply(disputeToTest);
                int numDisputesPerId = disputesPerIdMap.get(field).keys().count(id);
                checkArgument(numDisputesPerId <= 2,
                        "We found more than 2 disputes with the same %s. " +
                                "Trade ID: %s", field, disputeToTestTradeId);
            }
        } catch (IllegalArgumentException e) {
            throw new DisputeReplayException(disputeToTest, e.getMessage());
        } catch (NullPointerException e) {
            log.error("NullPointerException at testIfDisputeTriesReplay: " +
                    "disputeToTest={}, disputesPerIdMap={}", disputeToTest, disputesPerIdMap);
            throw new DisputeReplayException(disputeToTest, e + " at dispute " + disputeToTest);
        }
    }

    private enum DisputeIdField implements Function<Dispute, String> {
        TRADE_ID(Dispute::getTradeId),
        DELAYED_PAYOUT_TX_ID(Dispute::getDelayedPayoutTxId),
        WARNING_TX_ID(Dispute::getWarningTxId),
        REDIRECT_TX_ID(Dispute::getRedirectTxId),
        DEPOSIT_TX_ID(Dispute::getDepositTxId);

        private final Function<Dispute, String> getter;

        DisputeIdField(Function<Dispute, String> getter) {
            this.getter = getter;
        }

        @Override
        public String apply(Dispute dispute) {
            return getter.apply(dispute);
        }

        @Override
        public String toString() {
            return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
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
