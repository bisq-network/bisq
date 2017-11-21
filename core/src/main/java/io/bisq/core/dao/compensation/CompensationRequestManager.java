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

package io.bisq.core.dao.compensation;

import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bisq.common.proto.persistable.PersistableList;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.Storage;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.DaoPeriodService;
import io.bisq.core.dao.vote.VotingDefaultValues;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CompensationRequestManager implements PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(CompensationRequestManager.class);

    private static final int GENESIS_BLOCK_HEIGHT = 391; // TODO dev version regtest

    private final P2PService p2PService;
    private final DaoPeriodService daoPeriodService;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final CompensationRequestModel model;
    private final VotingDefaultValues votingDefaultValues;
    private final Storage<PersistableList<CompensationRequest>> compensationRequestsStorage;

    private CompensationRequest selectedCompensationRequest;
    private int bestChainHeight = -1;
    @Getter
    private final ObservableList<CompensationRequest> observableList;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationRequestManager(P2PService p2PService,
                                      BtcWalletService btcWalletService,
                                      BsqWalletService bsqWalletService,
                                      DaoPeriodService daoPeriodService,
                                      CompensationRequestModel model,
                                      VotingDefaultValues votingDefaultValues,
                                      Storage<PersistableList<CompensationRequest>> compensationRequestsStorage) {
        this.p2PService = p2PService;
        this.daoPeriodService = daoPeriodService;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.model = model;
        this.votingDefaultValues = votingDefaultValues;
        this.compensationRequestsStorage = compensationRequestsStorage;

        observableList = FXCollections.observableArrayList(model.getList());

        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            p2PService.addHashSetChangedListener(new HashMapChangedListener() {
                @Override
                public void onAdded(ProtectedStorageEntry data) {
                    final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
                    if (protectedStoragePayload instanceof CompensationRequestPayload)
                        addToList((CompensationRequestPayload) protectedStoragePayload, true);
                }

                @Override
                public void onRemoved(ProtectedStorageEntry data) {
                    // TODO
                }
            });

            // At startup the P2PDataStorage inits earlier, otherwise we ge the listener called.
            p2PService.getP2PDataStorage().getMap().values().forEach(e -> {
                final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                if (protectedStoragePayload instanceof CompensationRequestPayload)
                    addToList((CompensationRequestPayload) protectedStoragePayload, false);
            });
        }
    }

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            PersistableList<CompensationRequest> persisted = compensationRequestsStorage.initAndGetPersistedWithFileName("CompensationRequests", 100);
            if (persisted != null)
                model.setPersistedCompensationRequest(persisted.getList());
        }
    }

    public void onAllServicesInitialized() {
        /*if (daoPeriodService.getPhase() == DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS) {

        }*/
    }

    public void addToP2PNetwork(CompensationRequestPayload compensationRequestPayload) {
        p2PService.addProtectedStorageEntry(compensationRequestPayload, true);
    }

    public void addToList(CompensationRequestPayload compensationRequestPayload, boolean storeLocally) {
        if (!contains(compensationRequestPayload)) {
            model.addCompensationRequest(new CompensationRequest(compensationRequestPayload));
            if (storeLocally)
                compensationRequestsStorage.queueUpForSave(new PersistableList<>(model.getList()), 500);
        } else {
            log.warn("We have already an item with the same CompensationRequest.");
        }
    }

    private boolean contains(CompensationRequestPayload compensationRequestPayload) {
        return model.getList().stream().filter(e -> e.getCompensationRequestPayload().equals(compensationRequestPayload)).findAny().isPresent();
    }

    public List<CompensationRequest> getCompensationRequestsList() {
        return model.getList();
    }

    public void fundCompensationRequest(CompensationRequest compensationRequest, Coin amount, FutureCallback<Transaction> callback) {
        btcWalletService.fundCompensationRequest(amount, compensationRequest.getCompensationRequestPayload().getBtcAddress(), bsqWalletService.getUnusedAddress(), callback);
    }

    public void setSelectedCompensationRequest(CompensationRequest selectedCompensationRequest) {
        this.selectedCompensationRequest = selectedCompensationRequest;
    }

    public CompensationRequest getSelectedCompensationRequest() {
        return selectedCompensationRequest;
    }
}
