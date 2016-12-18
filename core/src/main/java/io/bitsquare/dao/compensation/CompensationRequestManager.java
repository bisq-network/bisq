/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.compensation;

import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bitsquare.storage.Storage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class CompensationRequestManager {
    private static final Logger log = LoggerFactory.getLogger(CompensationRequestManager.class);

    private P2PService p2PService;
    private Storage<ArrayList<CompensationRequest>> compensationRequestsStorage;
    private BtcWalletService btcWalletService;
    private SquWalletService squWalletService;
    private ObservableList<CompensationRequest> observableCompensationRequestsList = FXCollections.observableArrayList();
    private CompensationRequest selectedCompensationRequest;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationRequestManager(P2PService p2PService, Storage<ArrayList<CompensationRequest>> compensationRequestsStorage,
                                      BtcWalletService btcWalletService, SquWalletService squWalletService) {
        this.p2PService = p2PService;
        this.compensationRequestsStorage = compensationRequestsStorage;
        this.btcWalletService = btcWalletService;
        this.squWalletService = squWalletService;

        init(compensationRequestsStorage);
    }

    private void init(Storage<ArrayList<CompensationRequest>> compensationRequestsStorage) {
        ArrayList<CompensationRequest> persisted = compensationRequestsStorage.initAndGetPersistedWithFileName("CompensationRequests");
        if (persisted != null)
            observableCompensationRequestsList.addAll(persisted);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final StoragePayload storagePayload = data.getStoragePayload();
                if (storagePayload instanceof CompensationRequestPayload)
                    addToList((CompensationRequestPayload) storagePayload, true);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });

        // At startup the P2PDataStorage inits earlier, otherwise we ge the listener called.
        p2PService.getP2PDataStorage().getMap().values().forEach(e -> {
            final StoragePayload storagePayload = e.getStoragePayload();
            if (storagePayload instanceof CompensationRequestPayload)
                addToList((CompensationRequestPayload) storagePayload, false);
        });
    }

    public void addToP2PNetwork(CompensationRequestPayload compensationRequestPayload) {
        p2PService.addData(compensationRequestPayload, true);
    }

    public void addToList(CompensationRequestPayload compensationRequestPayload, boolean storeLocally) {
        if (!observableCompensationRequestsList.stream().filter(e -> e.getCompensationRequestPayload().equals(compensationRequestPayload)).findAny().isPresent()) {
            observableCompensationRequestsList.add(new CompensationRequest(compensationRequestPayload));
            if (storeLocally)
                compensationRequestsStorage.queueUpForSave(new ArrayList<>(observableCompensationRequestsList), 500);
        } else {
            log.warn("We have already an item with the same CompensationRequest.");
        }
    }

    public ObservableList<CompensationRequest> getObservableCompensationRequestsList() {
        return observableCompensationRequestsList;
    }

    public void fundCompensationRequest(CompensationRequest compensationRequest, Coin amount, FutureCallback<Transaction> callback) {
        btcWalletService.fundCompensationRequest(amount, compensationRequest.getCompensationRequestPayload().btcAddress, squWalletService.getUnusedAddress(), callback);
    }

    public void setSelectedCompensationRequest(CompensationRequest selectedCompensationRequest) {
        this.selectedCompensationRequest = selectedCompensationRequest;
    }

    public CompensationRequest getSelectedCompensationRequest() {
        return selectedCompensationRequest;
    }


}
