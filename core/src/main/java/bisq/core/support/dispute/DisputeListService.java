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

import bisq.core.trade.model.bisq_v1.Contract;

import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import org.fxmisc.easybind.EasyBind;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.ObservableList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class DisputeListService<T extends DisputeList<Dispute>> implements PersistedDataHost {
    @Getter
    protected final PersistenceManager<T> persistenceManager;
    @Getter
    private final T disputeList;
    @Getter
    private final IntegerProperty numOpenDisputes = new SimpleIntegerProperty();
    @Getter
    private final Set<String> disputedTradeIds = new HashSet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeListService(PersistenceManager<T> persistenceManager) {
        this.persistenceManager = persistenceManager;
        disputeList = getConcreteDisputeList();

        this.persistenceManager.initialize(disputeList, getFileName(), PersistenceManager.Source.PRIVATE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract T getConcreteDisputeList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(getFileName(), persisted -> {
                    disputeList.setAll(persisted.getList());
                    completeHandler.run();
                },
                completeHandler);
    }

    protected String getFileName() {
        return disputeList.getDefaultStorageFileName();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanupDisputes(@Nullable Consumer<String> closedDisputeHandler) {
        disputeList.stream().forEach(dispute -> {
            String tradeId = dispute.getTradeId();
            if (dispute.isClosed() && closedDisputeHandler != null) {
                closedDisputeHandler.accept(tradeId);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onAllServicesInitialized() {
        disputeList.addListener(change -> {
            change.next();
            onDisputesChangeListener(change.getAddedSubList(), change.getRemoved());
        });
        onDisputesChangeListener(disputeList.getList(), null);
    }

    String getNrOfDisputes(boolean isBuyer, Contract contract) {
        return String.valueOf(getObservableList().stream()
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

    ObservableList<Dispute> getObservableList() {
        return disputeList.getObservableList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onDisputesChangeListener(List<? extends Dispute> addedList,
                                          @Nullable List<? extends Dispute> removedList) {
        if (removedList != null) {
            removedList.forEach(dispute -> {
                disputedTradeIds.remove(dispute.getTradeId());
            });
        }
        addedList.forEach(dispute -> {
            // for each dispute added, keep track of its "BadgeCountProperty"
            EasyBind.subscribe(dispute.getBadgeCountProperty(),
                    isAlerting -> {
                        // We get the event before the list gets updated, so we execute on next frame
                        UserThread.execute(() -> {
                            int numAlerts = (int) disputeList.getList().stream()
                                    .mapToLong(x -> x.getBadgeCountProperty().getValue())
                                    .sum();
                            numOpenDisputes.set(numAlerts);
                        });
                    });
            disputedTradeIds.add(dispute.getTradeId());
        });
    }

    public void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
