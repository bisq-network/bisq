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

package bisq.core.support.dispute.agent;

import bisq.core.locale.Res;
import bisq.core.payment.payload.PayloadWithHolderName;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import javafx.collections.ListChangeListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects traders who had disputes where they used different account holder names. Only payment methods where a
 * real name is required are used for the check.
 * Strings are not translated here as it is only visible to dispute agents
 */
@Slf4j
public class MultipleHolderNameDetection {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onSuspiciousDisputeDetected();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final String ACK_KEY = "Ack-";

    private static String getSigPuKeyHashAsHex(PubKeyRing pubKeyRing) {
        return Utilities.encodeToHex(Hash.getRipemd160hash(pubKeyRing.getSignaturePubKeyBytes()));
    }

    private static String getSigPubKeyHashAsHex(Dispute dispute) {
        return getSigPuKeyHashAsHex(dispute.getTraderPubKeyRing());
    }

    private static boolean isBuyer(Dispute dispute) {
        String traderSigPubKeyHashAsHex = getSigPubKeyHashAsHex(dispute);
        String buyerSigPubKeyHashAsHex = getSigPuKeyHashAsHex(dispute.getContract().getBuyerPubKeyRing());
        return buyerSigPubKeyHashAsHex.equals(traderSigPubKeyHashAsHex);
    }

    private static Optional<PayloadWithHolderName> getPayloadWithHolderName(Dispute dispute) {
        Optional<PaymentAccountPayload> paymentAccountPayload = getPaymentAccountPayload(dispute);
        return paymentAccountPayload.map(accountPayload -> (PayloadWithHolderName) accountPayload);
    }

    public static Optional<PaymentAccountPayload> getPaymentAccountPayload(Dispute dispute) {
        return Optional.ofNullable(isBuyer(dispute) ?
                dispute.getContract().getBuyerPaymentAccountPayload() :
                dispute.getContract().getSellerPaymentAccountPayload());
    }

    public static String getAddress(Dispute dispute) {
        return isBuyer(dispute) ?
                dispute.getContract().getBuyerNodeAddress().getHostName() :
                dispute.getContract().getSellerNodeAddress().getHostName();
    }

    public static String getAckKey(Dispute dispute) {
        return ACK_KEY + getSigPubKeyHashAsHex(dispute).substring(0, 4) + "/" + dispute.getShortTradeId();
    }

    private static String getIsBuyerSubString(boolean isBuyer) {
        return "'\n        Role: " + (isBuyer ? "'Buyer'" : "'Seller'");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final DisputeManager<? extends DisputeList<Dispute>> disputeManager;

    // Key is hex of hash of sig pubKey which we consider a trader identity. We could use onion address as well but
    // once we support multiple onion addresses that would not work anymore.
    @Getter
    private final Map<String, List<Dispute>> suspiciousDisputesByTraderMap = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MultipleHolderNameDetection(DisputeManager<? extends DisputeList<Dispute>> disputeManager) {
        this.disputeManager = disputeManager;

        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) c -> {
            c.next();
            if (c.wasAdded()) {
                detectMultipleHolderNames();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void detectMultipleHolderNames() {
        String previous = suspiciousDisputesByTraderMap.toString();
        getAllDisputesByTraderMap().forEach((key, value) -> {
            Set<String> userNames = value.stream()
                    .map(dispute -> {
                        Optional<PayloadWithHolderName> payloadWithHolderName = getPayloadWithHolderName(dispute);
                        return payloadWithHolderName.map(PayloadWithHolderName::getHolderName).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (userNames.size() > 1) {
                // As we compare previous results we need to make sorting deterministic
                value.sort(Comparator.comparing(Dispute::getId));
                suspiciousDisputesByTraderMap.put(key, value);
            }
        });
        String updated = suspiciousDisputesByTraderMap.toString();
        if (!previous.equals(updated)) {
            listeners.forEach(Listener::onSuspiciousDisputeDetected);
        }
    }

    public boolean hasSuspiciousDisputesDetected() {
        return !suspiciousDisputesByTraderMap.isEmpty();
    }

    // Returns all disputes of a trader who used multiple names
    public List<Dispute> getDisputesForTrader(Dispute dispute) {
        String traderPubKeyHash = getSigPubKeyHashAsHex(dispute);
        if (suspiciousDisputesByTraderMap.containsKey(traderPubKeyHash)) {
            return suspiciousDisputesByTraderMap.get(traderPubKeyHash);
        }
        return new ArrayList<>();
    }

    // Get a report of traders who used multiple names with all their disputes listed
    public String getReportForAllDisputes() {
        return getReport(suspiciousDisputesByTraderMap.values());
    }

    // Get a report for a trader who used multiple names with all their disputes listed
    public String getReportForDisputeOfTrader(List<Dispute> disputes) {
        Collection<List<Dispute>> values = new ArrayList<>();
        values.add(disputes);
        return getReport(values);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Map<String, List<Dispute>> getAllDisputesByTraderMap() {
        Map<String, List<Dispute>> allDisputesByTraderMap = new HashMap<>();
        disputeManager.getDisputesAsObservableList().stream()
                .filter(dispute -> {
                    Contract contract = dispute.getContract();
                    PaymentAccountPayload paymentAccountPayload = isBuyer(dispute) ?
                            contract.getBuyerPaymentAccountPayload() :
                            contract.getSellerPaymentAccountPayload();
                    return paymentAccountPayload instanceof PayloadWithHolderName;
                })
                .forEach(dispute -> {
                    String traderPubKeyHash = getSigPubKeyHashAsHex(dispute);
                    allDisputesByTraderMap.putIfAbsent(traderPubKeyHash, new ArrayList<>());
                    List<Dispute> disputes = allDisputesByTraderMap.get(traderPubKeyHash);
                    disputes.add(dispute);
                });
        return allDisputesByTraderMap;
    }

    // Get a text report for a trader who used multiple names and list all the his disputes
    private String getReport(Collection<List<Dispute>> collectionOfDisputesOfTrader) {
        return collectionOfDisputesOfTrader.stream()
                .map(disputes -> {
                    Set<String> addresses = new HashSet<>();
                    Set<Boolean> isBuyerHashSet = new HashSet<>();
                    Set<String> names = new HashSet<>();
                    String disputesReport = disputes.stream()
                            .map(dispute -> {
                                addresses.add(getAddress(dispute));
                                String ackKey = getAckKey(dispute);
                                String ackSubString = "    ";
                                if (!DontShowAgainLookup.showAgain(ackKey)) {
                                    ackSubString = "[ACK]   ";
                                }
                                Optional<PayloadWithHolderName> payloadWithHolderName = getPayloadWithHolderName(dispute);
                                String holderName = payloadWithHolderName.isPresent() ? payloadWithHolderName.get().getHolderName() : "NA";
                                names.add(holderName);
                                boolean isBuyer = isBuyer(dispute);
                                isBuyerHashSet.add(isBuyer);
                                String isBuyerSubString = getIsBuyerSubString(isBuyer);
                                DisputeResult disputeResult = dispute.disputeResultProperty().get();
                                String summaryNotes = disputeResult != null ? disputeResult.getSummaryNotesProperty().get().trim() : "Not closed yet";
                                Optional<PaymentAccountPayload> paymentAccountPayload = getPaymentAccountPayload(dispute);
                                return ackSubString +
                                        "Trade ID: '" + dispute.getShortTradeId() +
                                        "'\n        Account holder name: '" + holderName +
                                        "'\n        Payment method: '" + Res.get(paymentAccountPayload.isPresent() ?
                                        paymentAccountPayload.get().getPaymentMethodId() : "NA") +
                                        isBuyerSubString +
                                        "'\n        Summary: '" + summaryNotes;
                            })
                            .collect(Collectors.joining("\n"));

                    String addressSubString = addresses.size() > 1 ?
                            "used multiple addresses " + addresses + " with" :
                            "with address " + new ArrayList<>(addresses).get(0) + " used";

                    String roleSubString = "Trader ";
                    if (isBuyerHashSet.size() == 1) {
                        boolean isBuyer = new ArrayList<>(isBuyerHashSet).get(0);
                        String isBuyerSubString = getIsBuyerSubString(isBuyer);
                        disputesReport = disputesReport.replace(isBuyerSubString, "");
                        roleSubString = isBuyer ? "Buyer " : "Seller ";
                    }


                    String traderReport = roleSubString + addressSubString + " multiple names: " + names.toString() + "\n" + disputesReport;
                    return new Tuple2<>(roleSubString, traderReport);
                })
                .sorted(Comparator.comparing(o -> o.first)) // Buyers first, then seller, then mixed (trader was in seller and buyer role)
                .map(e -> e.second)
                .collect(Collectors.joining("\n\n"));
    }
}
