/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.blindvote;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.blindvote.storage.BlindVotePayload;
import bisq.core.dao.governance.blindvote.storage.BlindVoteStorageService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for new BlindVotePayload and adds it to appendOnlyStoreList.
 */
@Slf4j
public class BlindVoteListService implements AppendOnlyDataStoreListener, DaoStateListener, DaoSetupService {
    private final DaoStateService daoStateService;
    private final P2PService p2PService;
    private final BlindVoteValidator blindVoteValidator;
    @Getter
    private final ObservableList<BlindVotePayload> blindVotePayloads = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteListService(DaoStateService daoStateService,
                                P2PService p2PService,
                                BlindVoteStorageService blindVoteStorageService,
                                AppendOnlyDataStoreService appendOnlyDataStoreService,
                                BlindVoteValidator blindVoteValidator,
                                @Named(DaoOptionKeys.DAO_ACTIVATED) boolean daoActivated) {
        this.daoStateService = daoStateService;
        this.p2PService = p2PService;
        this.blindVoteValidator = blindVoteValidator;

        if (daoActivated)
            appendOnlyDataStoreService.addService(blindVoteStorageService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
    }

    @Override
    public void start() {
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockChainComplete() {
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onAppendOnlyDataAdded(payload, true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<BlindVote> getBlindVotesInPhaseAndCycle() {
        return blindVotePayloads.stream()
                .filter(blindVotePayload -> blindVoteValidator.isTxInPhaseAndCycle(blindVotePayload.getBlindVote()))
                .map(BlindVotePayload::getBlindVote)
                .collect(Collectors.toList());
    }

    public List<BlindVote> getConfirmedBlindVotes() {
        return blindVotePayloads.stream()
                .filter(blindVotePayload -> blindVoteValidator.areDataFieldsValidAndTxConfirmed(blindVotePayload.getBlindVote()))
                .map(BlindVotePayload::getBlindVote)
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillListFromAppendOnlyDataStore() {
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(e -> onAppendOnlyDataAdded(e, false));
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload, boolean doLog) {
        if (persistableNetworkPayload instanceof BlindVotePayload) {
            BlindVotePayload blindVotePayload = (BlindVotePayload) persistableNetworkPayload;
            if (!blindVotePayloads.contains(blindVotePayload)) {
                BlindVote blindVote = blindVotePayload.getBlindVote();
                String txId = blindVote.getTxId();
                // We don't check the phase and the cycle as we want to add all object independently when we receive it
                // (or when we start the app to fill our list from the data we gor from the seed node).
                if (blindVoteValidator.areDataFieldsValid(blindVote)) {
                    // We don't validate as we might receive blindVotes from other cycles or phases at startup.
                    blindVotePayloads.add(blindVotePayload);
                    if (doLog) {
                        log.info("We received a blindVotePayload. blindVoteTxId={}", txId);
                    }
                } else {
                    log.warn("We received an invalid blindVotePayload. blindVoteTxId={}", txId);
                }
            }
        }
    }
}
