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

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.blindvote.storage.BlindVotePayload;
import bisq.core.dao.governance.blindvote.storage.BlindVoteStorageService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.DaoPhase;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.config.Config;

import javax.inject.Inject;

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
    private final PeriodService periodService;
    private final BlindVoteStorageService blindVoteStorageService;
    private final BlindVoteValidator blindVoteValidator;
    @Getter
    private final ObservableList<BlindVotePayload> blindVotePayloads = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteListService(DaoStateService daoStateService,
                                P2PService p2PService,
                                PeriodService periodService,
                                BlindVoteStorageService blindVoteStorageService,
                                AppendOnlyDataStoreService appendOnlyDataStoreService,
                                BlindVoteValidator blindVoteValidator,
                                Config config) {
        this.daoStateService = daoStateService;
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.blindVoteStorageService = blindVoteStorageService;
        this.blindVoteValidator = blindVoteValidator;

        if (config.daoActivated)
            appendOnlyDataStoreService.addService(blindVoteStorageService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
    }

    @Override
    public void start() {
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
        // We only add blindVotes to blindVoteStorageService if we are not in the vote reveal phase.
        blindVoteStorageService.setNotInVoteRevealPhase(notInVoteRevealPhase(blockHeight));
    }

    @Override
    public void onParseBlockChainComplete() {
        fillListFromAppendOnlyDataStore();

        // We set the listener after parsing is complete to be sure we have a consistent state for the phase check.
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
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
        blindVoteStorageService.getMap().values().forEach(e -> onAppendOnlyDataAdded(e, false));
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload, boolean fromBroadcastMessage) {
        if (persistableNetworkPayload instanceof BlindVotePayload) {
            BlindVotePayload blindVotePayload = (BlindVotePayload) persistableNetworkPayload;
            if (!blindVotePayloads.contains(blindVotePayload)) {
                BlindVote blindVote = blindVotePayload.getBlindVote();
                String txId = blindVote.getTxId();

                if (blindVoteValidator.areDataFieldsValid(blindVote)) {
                    if (fromBroadcastMessage) {
                        if (notInVoteRevealPhase(daoStateService.getChainHeight())) {
                            // We received the payload outside the vote reveal phase and add the payload.
                            // If we would accept it during the vote reveal phase we would be vulnerable to a late
                            // publishing attack where the attacker tries to pollute the data view of the voters and
                            // render the whole voting cycle invalid if the majority hash is not at least 80% of the
                            // vote stake.
                            blindVotePayloads.add(blindVotePayload);
                        }
                    } else {
                        // In case we received the data from the seed node at startup we cannot apply the phase check as
                        // even in the vote reveal phase we want to receive missed blind votes.
                        blindVotePayloads.add(blindVotePayload);
                    }
                } else {
                    log.warn("We received an invalid blindVotePayload. blindVoteTxId={}", txId);
                }
            }
        }
    }

    private boolean notInVoteRevealPhase(int blockHeight) {
        return !periodService.isInPhase(blockHeight, DaoPhase.Phase.VOTE_REVEAL);
    }
}
