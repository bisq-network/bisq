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

package io.bitsquare.dao.proposals;

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

public class ProposalManager {
    private static final Logger log = LoggerFactory.getLogger(ProposalManager.class);

    private P2PService p2PService;
    private Storage<ArrayList<Proposal>> proposalsStorage;
    private BtcWalletService btcWalletService;
    private SquWalletService squWalletService;
    private ObservableList<Proposal> observableProposalsList = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalManager(P2PService p2PService, Storage<ArrayList<Proposal>> proposalsStorage,
                           BtcWalletService btcWalletService, SquWalletService squWalletService) {
        this.p2PService = p2PService;
        this.proposalsStorage = proposalsStorage;
        this.btcWalletService = btcWalletService;
        this.squWalletService = squWalletService;

        init(proposalsStorage);
    }

    private void init(Storage<ArrayList<Proposal>> proposalsStorage) {
        ArrayList<Proposal> persisted = proposalsStorage.initAndGetPersistedWithFileName("Proposals");
        if (persisted != null)
            observableProposalsList.addAll(persisted);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final StoragePayload storagePayload = data.getStoragePayload();
                if (storagePayload instanceof Proposal)
                    addToList((Proposal) storagePayload, true);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });

        // At startup the P2PDataStorage inits earlier, otherwise we ge the listener called.
        p2PService.getP2PDataStorage().getMap().values().forEach(e -> {
            final StoragePayload storagePayload = e.getStoragePayload();
            if (storagePayload instanceof Proposal)
                addToList((Proposal) storagePayload, false);
        });
    }

    public void addToP2PNetwork(Proposal proposal) {
        p2PService.addData(proposal, true);
    }

    public void addToList(Proposal proposal, boolean storeLocally) {
        if (!observableProposalsList.contains(proposal)) {
            observableProposalsList.add(proposal);
            if (storeLocally)
                proposalsStorage.queueUpForSave(new ArrayList<>(observableProposalsList), 500);
        } else {
            log.warn("We have already an item with the same proposal.");
        }
    }

    public ObservableList<Proposal> getObservableProposalsList() {
        return observableProposalsList;
    }

    public void fundProposal(Proposal proposal, Coin amount, FutureCallback<Transaction> callback) {
        btcWalletService.fundProposal(amount, proposal.btcAddress, squWalletService.getSquAddressForProposalFunding(), callback);
    }
}
