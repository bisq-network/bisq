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
import bisq.core.trade.Contract;

import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Utilities;

import javafx.collections.ListChangeListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FraudDetection {
    public interface Listener {
        void onSuspiciousDisputeDetected();
    }

    private final DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager;
    private Map<String, List<RealNameAccountInfo>> buyerRealNameAccountByAddressMap = new HashMap<>();
    private Map<String, List<RealNameAccountInfo>> sellerRealNameAccountByAddressMap = new HashMap<>();
    @Getter
    private Map<String, List<RealNameAccountInfo>> accountsUsingMultipleNames = new HashMap<>();
    private List<Listener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FraudDetection(DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager) {
        this.disputeManager = disputeManager;

        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) c -> {
            c.next();
            if (c.wasAdded()) {
                checkForMultipleHolderNames();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkForMultipleHolderNames() {
        log.error("checkForMultipleHolderNames");
        buildRealNameAccountMaps();
        detectUsageOfDifferentUserNames();
        log.error("hasSuspiciousDisputeDetected() " + hasSuspiciousDisputeDetected());
    }

    public boolean hasSuspiciousDisputeDetected() {
        return !accountsUsingMultipleNames.isEmpty();
    }

    public String getAccountsUsingMultipleNamesAsString() {
        return accountsUsingMultipleNames.entrySet().stream()
                .map(entry -> {
                    String accountInfo = entry.getValue().stream()
                            .map(info -> {
                                String tradeId = info.getDispute().getShortTradeId();
                                String holderName = info.getPayloadWithHolderName().getHolderName();
                                return "    Account owner name: '" + holderName +
                                        "'; Trade ID: '" + tradeId +
                                        "'; Address: '" + info.getAddress() +
                                        "'; Payment method: '" + Res.get(info.getPaymentAccountPayload().getPaymentMethodId()) +
                                        "'; Role: " + (info.isBuyer() ? "'Buyer'" : "'Seller'");

                            })
                            .collect(Collectors.joining("\n"));
                    return "Trader with multiple identities:\n" +
                            accountInfo;
                })
                .collect(Collectors.joining("\n\n"));
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

    private void buildRealNameAccountMaps() {
        buyerRealNameAccountByAddressMap.clear();
        sellerRealNameAccountByAddressMap.clear();
        disputeManager.getDisputesAsObservableList()
                .forEach(dispute -> {
                    Contract contract = dispute.getContract();
                    PubKeyRing traderPubKeyRing = dispute.getTraderPubKeyRing();
                    String traderPubKeyHash = getTraderPuKeyHash(traderPubKeyRing);
                    String buyerPubKeyHash = getTraderPuKeyHash(contract.getBuyerPubKeyRing());
                    boolean isBuyer = contract.isMyRoleBuyer(traderPubKeyRing);

                    if (buyerPubKeyHash.equals(traderPubKeyHash)) {
                        PaymentAccountPayload buyerPaymentAccountPayload = contract.getBuyerPaymentAccountPayload();
                        String buyersAddress = contract.getBuyerNodeAddress().getFullAddress();
                        addToMap(traderPubKeyHash, buyerRealNameAccountByAddressMap, buyerPaymentAccountPayload, buyersAddress, dispute, isBuyer);
                    } else {
                        PaymentAccountPayload sellerPaymentAccountPayload = contract.getSellerPaymentAccountPayload();
                        String sellerAddress = contract.getSellerNodeAddress().getFullAddress();
                        addToMap(traderPubKeyHash, sellerRealNameAccountByAddressMap, sellerPaymentAccountPayload, sellerAddress, dispute, isBuyer);
                    }
                });
    }

    private String getTraderPuKeyHash(PubKeyRing pubKeyRing) {
        return Utilities.encodeToHex(Hash.getRipemd160hash(pubKeyRing.toProtoMessage().toByteArray()));
    }

    private void addToMap(String pubKeyHash, Map<String, List<RealNameAccountInfo>> map,
                          PaymentAccountPayload paymentAccountPayload,
                          String address,
                          Dispute dispute,
                          boolean isBuyer) {
        if (paymentAccountPayload instanceof PayloadWithHolderName) {
            map.putIfAbsent(pubKeyHash, new ArrayList<>());
            RealNameAccountInfo info = new RealNameAccountInfo(address,
                    (PayloadWithHolderName) paymentAccountPayload,
                    paymentAccountPayload,
                    dispute,
                    isBuyer);
            map.get(pubKeyHash).add(info);
        }
    }

    private void detectUsageOfDifferentUserNames() {
        detectUsageOfDifferentUserNames(buyerRealNameAccountByAddressMap);
        detectUsageOfDifferentUserNames(sellerRealNameAccountByAddressMap);
    }

    private void detectUsageOfDifferentUserNames(Map<String, List<RealNameAccountInfo>> map) {
        String previous = accountsUsingMultipleNames.toString();
        map.forEach((key, value) -> {
            Set<String> userNames = value.stream()
                    .map(info -> info.getPayloadWithHolderName().getHolderName())
                    .collect(Collectors.toSet());
            if (userNames.size() > 1) {
                accountsUsingMultipleNames.put(key, value);
            }
        });
        String updated = accountsUsingMultipleNames.toString();
        if (!previous.equals(updated)) {
            listeners.forEach(Listener::onSuspiciousDisputeDetected);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static class
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Value
    private static class RealNameAccountInfo {
        private final String address;
        private final PayloadWithHolderName payloadWithHolderName;
        private final Dispute dispute;
        private final boolean isBuyer;
        private final PaymentAccountPayload paymentAccountPayload;

        RealNameAccountInfo(String address,
                            PayloadWithHolderName payloadWithHolderName,
                            PaymentAccountPayload paymentAccountPayload,
                            Dispute dispute,
                            boolean isBuyer) {
            this.address = address;
            this.payloadWithHolderName = payloadWithHolderName;
            this.paymentAccountPayload = paymentAccountPayload;
            this.dispute = dispute;
            this.isBuyer = isBuyer;
        }
    }
}
