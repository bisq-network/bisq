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

package bisq.core.dao.governance.proposal;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalStorageService;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalPayload;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalStorageService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains protectedStoreList and appendOnlyStoreList for received proposals.
 * Republishes protectedStoreList to append-only data store when entering the break before the blind vote phase.
 */
@Slf4j
public class ProposalService implements HashMapChangedListener, AppendOnlyDataStoreListener,
        DaoStateListener, DaoSetupService {
    private final P2PService p2PService;
    private final PeriodService periodService;
    private final DaoStateService daoStateService;
    private final ProposalValidator proposalValidator;

    // Proposals we receive in the proposal phase. They can be removed in that phase. That list must not be used for
    // consensus critical code.
    @Getter
    private final ObservableList<Proposal> tempProposals = FXCollections.observableArrayList();

    // Proposals which got added to the append-only data store in the break before the blind vote phase.
    // They cannot be removed anymore. This list is used for consensus critical code. Different nodes might have
    // different data collections due the eventually consistency of the P2P network.
    @Getter
    private final ObservableList<ProposalPayload> proposalPayloads = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalService(P2PService p2PService,
                           PeriodService periodService,
                           ProposalStorageService proposalStorageService,
                           TempProposalStorageService tempProposalStorageService,
                           AppendOnlyDataStoreService appendOnlyDataStoreService,
                           ProtectedDataStoreService protectedDataStoreService,
                           DaoStateService daoStateService,
                           ProposalValidator proposalValidator,
                           @Named(DaoOptionKeys.DAO_ACTIVATED) boolean daoActivated) {
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.daoStateService = daoStateService;
        this.proposalValidator = proposalValidator;

        if (daoActivated) {
            // We add our stores to the global stores
            appendOnlyDataStoreService.addService(proposalStorageService);
            protectedDataStoreService.addService(tempProposalStorageService);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
        // Listen for tempProposals
        p2PService.addHashSetChangedListener(this);
        // Listen for proposalPayloads
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
    }

    @Override
    public void start() {
        fillListFromProtectedStore();
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onProtectedDataAdded(entry, true);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry entry) {
        onProtectedDataRemoved(entry);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onAppendOnlyDataAdded(payload, true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        int heightForRepublishing = periodService.getFirstBlockOfPhase(daoStateService.getChainHeight(), DaoPhase.Phase.BREAK1);
        if (block.getHeight() == heightForRepublishing) {
            // We only republish if we are completed with parsing old blocks, otherwise we would republish old
            // proposals all the time
            publishToAppendOnlyDataStore();
            fillListFromAppendOnlyDataStore();
        }
    }

    @Override
    public void onParseBlockChainComplete() {
        // Fill the lists with the data we have collected in out stores.
        fillListFromProtectedStore();
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<Proposal> getValidatedProposals() {
        return proposalPayloads.stream()
                .map(ProposalPayload::getProposal)
                .filter(proposalValidator::isTxTypeValid)
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillListFromProtectedStore() {
        p2PService.getDataMap().values().forEach(e -> onProtectedDataAdded(e, false));
    }

    private void fillListFromAppendOnlyDataStore() {
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(e -> onAppendOnlyDataAdded(e, false));
    }

    private void publishToAppendOnlyDataStore() {
        tempProposals.stream()
                .filter(proposalValidator::isValidAndConfirmed)
                .map(ProposalPayload::new)
                .forEach(proposalPayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(proposalPayload, true);
                    if (success)
                        log.info("We published a ProposalPayload to the P2P network as append-only data. proposalTxId={}",
                                proposalPayload.getProposal().getTxId());
                    else
                        log.warn("publishToAppendOnlyDataStore failed for proposal " + proposalPayload.getProposal());
                });
    }

    private void onProtectedDataAdded(ProtectedStorageEntry entry, boolean doLog) {
        ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof TempProposalPayload) {
            Proposal proposal = ((TempProposalPayload) protectedStoragePayload).getProposal();
            // We do not validate if we are in current cycle and if tx is confirmed yet as the tx might be not
            // available/confirmed. But we check if we are in the proposal phase.
            if (!tempProposals.contains(proposal)) {
                if (proposalValidator.isValidOrUnconfirmed(proposal)) {
                    if (doLog) {
                        log.info("We received a TempProposalPayload and store it to our protectedStoreList. proposalTxId={}",
                                proposal.getTxId());
                    }
                    tempProposals.add(proposal);
                } else {
                    log.debug("We received an invalid proposal from the P2P network. Proposal.txId={}, blockHeight={}",
                            proposal.getTxId(), daoStateService.getChainHeight());
                }
            }
        }
    }

    private void onProtectedDataRemoved(ProtectedStorageEntry entry) {
        ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof TempProposalPayload) {
            Proposal proposal = ((TempProposalPayload) protectedStoragePayload).getProposal();
            // We allow removal only if we are in the proposal phase.
            boolean inPhase = periodService.isInPhase(daoStateService.getChainHeight(), DaoPhase.Phase.PROPOSAL);
            boolean txInPastCycle = periodService.isTxInPastCycle(proposal.getTxId(), daoStateService.getChainHeight());
            Optional<Tx> tx = daoStateService.getTx(proposal.getTxId());
            boolean unconfirmedOrNonBsqTx = !tx.isPresent();
            // if the tx is unconfirmed we need to be in the PROPOSAL phase, otherwise the tx must be confirmed.
            if (inPhase || txInPastCycle || unconfirmedOrNonBsqTx) {
                if (tempProposals.contains(proposal)) {
                    tempProposals.remove(proposal);
                    log.info("We received a remove request for a TempProposalPayload and have removed the proposal " +
                                    "from our list. proposal creation date={}, proposalTxId={}, inPhase={}, " +
                                    "txInPastCycle={}, unconfirmedOrNonBsqTx={}",
                            proposal.getCreationDate(), proposal.getTxId(), inPhase, txInPastCycle, unconfirmedOrNonBsqTx);
                }
            } else {
                log.warn("We received a remove request outside the PROPOSAL phase. " +
                                "Proposal creation date={}, proposal.txId={}, current blockHeight={}",
                        proposal.getCreationDate(), proposal.getTxId(), daoStateService.getChainHeight());
            }
        }
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload, boolean doLog) {
        if (persistableNetworkPayload instanceof ProposalPayload) {
            ProposalPayload proposalPayload = (ProposalPayload) persistableNetworkPayload;
            if (!proposalPayloads.contains(proposalPayload)) {
                Proposal proposal = proposalPayload.getProposal();
                if (proposalValidator.areDataFieldsValid(proposal)) {
                    if (doLog) {
                        log.info("We received a ProposalPayload and store it to our appendOnlyStoreList. proposalTxId={}",
                                proposal.getTxId());
                    }
                    proposalPayloads.add(proposalPayload);
                } else {
                    log.warn("We received a invalid append-only proposal from the P2P network. " +
                                    "Proposal.txId={}, blockHeight={}",
                            proposal.getTxId(), daoStateService.getChainHeight());
                }
            }
        }
    }
}
