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

import bisq.core.trade.Contract;

import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class DisputeListService<T extends DisputeList<? extends DisputeList>> implements PersistedDataHost {
    @Getter
    protected final Storage<T> storage;
    @Nullable
    @Getter
    private T disputeList;
    private final Map<String, Dispute> openDisputes = new HashMap<>();
    private final Map<String, Dispute> closedDisputes = new HashMap<>();
    private final Map<String, Subscription> disputeIsClosedSubscriptionsMap = new HashMap<>();
    @Getter
    private final IntegerProperty numOpenDisputes = new SimpleIntegerProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeListService(Storage<T> storage) {
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract T getConcreteDisputeList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        disputeList = getConcreteDisputeList();
        disputeList.readPersisted();
        disputeList.stream().forEach(dispute -> dispute.setStorage(storage));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onAllServicesInitialized() {
        cleanupDisputes(null);

        if (disputeList != null) {
            disputeList.getList().addListener((ListChangeListener<Dispute>) change -> {
                change.next();
                onDisputesChangeListener(change.getAddedSubList(), change.getRemoved());
            });
            onDisputesChangeListener(disputeList.getList(), null);
        } else {
            log.warn("disputes is null");
        }
    }

    String getNrOfDisputes(boolean isBuyer, Contract contract) {
        return String.valueOf(getDisputesAsObservableList().stream()
                .filter(e -> {
                    Contract contract1 = e.getContract();
                    if (contract1 == null)
                        return false;

                    if (isBuyer) {
                        NodeAddress buyerNodeAddress = contract1.getBuyerNodeAddress();
                        return buyerNodeAddress != null && buyerNodeAddress.equals(contract.getBuyerNodeAddress());
                    } else {
                        NodeAddress sellerNodeAddress = contract1.getSellerNodeAddress();
                        return sellerNodeAddress != null && sellerNodeAddress.equals(contract.getSellerNodeAddress());
                    }
                })
                .collect(Collectors.toSet()).size());
    }

    ObservableList<Dispute> getDisputesAsObservableList() {
        if (disputeList == null) {
            log.warn("disputes is null");
            return FXCollections.observableArrayList();
        }
        return disputeList.getList();
    }


    void cleanupDisputes(@Nullable Consumer<String> closeDisputedTradeHandler) {
        if (disputeList != null) {
            disputeList.stream().forEach(dispute -> {
                dispute.setStorage(storage);
                if (dispute.isClosed())
                    closedDisputes.put(dispute.getTradeId(), dispute);
                else
                    openDisputes.put(dispute.getTradeId(), dispute);
            });
        } else {
            log.warn("disputes is null");
        }

        // If we have duplicate disputes we close the second one (might happen if both traders opened a dispute and arbitrator
        // was offline, so could not forward msg to other peer, then the arbitrator might have 4 disputes open for 1 trade)
        openDisputes.forEach((key, openDispute) -> {
            if (closedDisputes.containsKey(key)) {
                Dispute closedDispute = closedDisputes.get(key);
                // We need to check if is from the same peer, we don't want to close the peers dispute
                if (closedDispute.getTraderId() == openDispute.getTraderId()) {
                    openDispute.setIsClosed(true);
                    if (closeDisputedTradeHandler != null) {
                        closeDisputedTradeHandler.accept(openDispute.getTradeId());
                    }
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onDisputesChangeListener(List<? extends Dispute> addedList,
                                          @Nullable List<? extends Dispute> removedList) {
        if (removedList != null) {
            removedList.forEach(dispute -> {
                String id = dispute.getId();
                if (disputeIsClosedSubscriptionsMap.containsKey(id)) {
                    disputeIsClosedSubscriptionsMap.get(id).unsubscribe();
                    disputeIsClosedSubscriptionsMap.remove(id);
                }
            });
        }
        addedList.forEach(dispute -> {
            String id = dispute.getId();
            Subscription disputeStateSubscription = EasyBind.subscribe(dispute.isClosedProperty(),
                    isClosed -> {
                        if (disputeList != null) {
                            // We get the event before the list gets updated, so we execute on next frame
                            UserThread.execute(() -> {
                                int openDisputes = (int) disputeList.getList().stream()
                                        .filter(e -> !e.isClosed()).count();
                                numOpenDisputes.set(openDisputes);
                            });
                        }
                    });
            disputeIsClosedSubscriptionsMap.put(id, disputeStateSubscription);
        });
    }

    public void persist() {
        if (disputeList != null) {
            disputeList.persist();
        }
    }
}
