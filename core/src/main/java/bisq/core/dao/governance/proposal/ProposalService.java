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

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalStorageService;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalPayload;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalStorageService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
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

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
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
    private final ProposalStorageService proposalStorageService;
    private final DaoStateService daoStateService;
    private final ProposalValidatorProvider validatorProvider;

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
                           ProposalValidatorProvider validatorProvider,
                           @Named(Config.DAO_ACTIVATED) boolean daoActivated) {
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.proposalStorageService = proposalStorageService;
        this.daoStateService = daoStateService;
        this.validatorProvider = validatorProvider;

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
    public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
        protectedStorageEntries.forEach(protectedStorageEntry -> {
            onProtectedDataAdded(protectedStorageEntry, true);
        });
    }

    @Override
    public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {
        onProtectedDataRemoved(protectedStorageEntries);
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
        // We try to broadcast at any block in the break1 phase. If we have received the data already we do not
        // broadcast so we do not flood the network.
        if (periodService.isInPhase(block.getHeight(), DaoPhase.Phase.BREAK1)) {
            // We only republish if we are completed with parsing old blocks, otherwise we would republish old
            // proposals all the time
            maybePublishToAppendOnlyDataStore();
            fillListFromAppendOnlyDataStore();
        }
    }

    @Override
    public void onParseBlockChainComplete() {
        // Fill the lists with the data we have collected in our stores.
        fillListFromProtectedStore();
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<Proposal> getValidatedProposals() {
        return proposalPayloads.stream()
                .map(ProposalPayload::getProposal)
                .filter(proposal -> validatorProvider.getValidator(proposal).isTxTypeValid(proposal))
                .collect(Collectors.toList());
    }

    public Coin getRequiredQuorum(Proposal proposal) {
        int chainHeight = daoStateService.getTx(proposal.getTxId())
                .map(BaseTx::getBlockHeight).
                        orElse(daoStateService.getChainHeight());
        return daoStateService.getParamValueAsCoin(proposal.getQuorumParam(), chainHeight);
    }

    public double getRequiredThreshold(Proposal proposal) {
        int chainHeight = daoStateService.getTx(proposal.getTxId())
                .map(BaseTx::getBlockHeight).
                        orElse(daoStateService.getChainHeight());
        return daoStateService.getParamValueAsPercentDouble(proposal.getThresholdParam(), chainHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillListFromProtectedStore() {
        p2PService.getDataMap().values().forEach(e -> onProtectedDataAdded(e, false));
    }

    private void fillListFromAppendOnlyDataStore() {
        proposalStorageService.getMap().values().forEach(e -> onAppendOnlyDataAdded(e, false));
    }

    private void maybePublishToAppendOnlyDataStore() {
        // We set reBroadcast to false to avoid to flood the network.
        // If we have 20 proposals and 200 nodes with 10 neighbor peers we would send 40 000 messages if we would set
        // reBroadcast to !
        tempProposals.stream()
                .filter(proposal -> validatorProvider.getValidator(proposal).isValidAndConfirmed(proposal))
                .map(ProposalPayload::new)
                .forEach(proposalPayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(proposalPayload, false);
                    if (success) {
                        log.info("We published a ProposalPayload to the P2P network as append-only data. proposalTxId={}",
                                proposalPayload.getProposal().getTxId());
                    }
                    // If we had data already we did not broadcast and success is false
                });
    }

    private void onProtectedDataAdded(ProtectedStorageEntry entry, boolean fromBroadcastMessage) {
        ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof TempProposalPayload) {
            Proposal proposal = ((TempProposalPayload) protectedStoragePayload).getProposal();
            // We do not validate if we are in current cycle and if tx is confirmed yet as the tx might be not
            // available/confirmed.
            // We check if we are in the proposal or break1 phase. We are tolerant to accept tempProposals in the break1
            // phase to avoid risks that a proposal published very closely to the end of the proposal phase will not be
            // sufficiently broadcast.
            // When we receive tempProposals from the seed node at startup we only keep those which are in the current
            // proposal/break1 phase if we are in that phase. We ignore tempProposals in case we are not in the
            // proposal/break1 phase as they are not used anyway but the proposalPayloads will be relevant once we
            // left the proposal/break1 phase.
            if (periodService.isInPhase(daoStateService.getChainHeight(), DaoPhase.Phase.PROPOSAL) ||
                    periodService.isInPhase(daoStateService.getChainHeight(), DaoPhase.Phase.BREAK1)) {
                if (!tempProposals.contains(proposal)) {
                    // We only validate in case the blocks are parsed as otherwise some validators like param validator
                    // might fail as Dao state is not complete.
                    if (!daoStateService.isParseBlockChainComplete() ||
                            validatorProvider.getValidator(proposal).areDataFieldsValid(proposal)) {
                        if (fromBroadcastMessage) {
                            log.info("We received a TempProposalPayload and store it to our protectedStoreList. proposalTxId={}",
                                    proposal.getTxId());
                        }
                        tempProposals.add(proposal);
                    } else {
                        log.debug("We received an invalid proposal from the P2P network. Proposal={}, blockHeight={}",
                                proposal, daoStateService.getChainHeight());
                    }
                }
            }
        }
    }

    private void onProtectedDataRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {

        // The listeners of tmpProposals can do large amounts of work that cause performance issues. Apply all of the
        // updates at once using retainAll which will cause all listeners to be updated only once.
        ArrayList<Proposal> tempProposalsWithUpdates = new ArrayList<>(tempProposals);

        protectedStorageEntries.forEach(protectedStorageEntry -> {
            ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
            if (protectedStoragePayload instanceof TempProposalPayload) {
                Proposal proposal = ((TempProposalPayload) protectedStoragePayload).getProposal();
                // We allow removal only if we are in the proposal phase.
                boolean inPhase = periodService.isInPhase(daoStateService.getChainHeight(), DaoPhase.Phase.PROPOSAL);
                boolean txInPastCycle = periodService.isTxInPastCycle(proposal.getTxId(), daoStateService.getChainHeight());
                Optional<Tx> tx = daoStateService.getTx(proposal.getTxId());
                boolean unconfirmedOrNonBsqTx = !tx.isPresent();
                // if the tx is unconfirmed we need to be in the PROPOSAL phase, otherwise the tx must be confirmed.
                if (inPhase || txInPastCycle || unconfirmedOrNonBsqTx) {
                    if (tempProposalsWithUpdates.contains(proposal)) {
                        tempProposalsWithUpdates.remove(proposal);
                        log.debug("We received a remove request for a TempProposalPayload and have removed the proposal " +
                                        "from our list. proposal creation date={}, proposalTxId={}, inPhase={}, " +
                                        "txInPastCycle={}, unconfirmedOrNonBsqTx={}",
                                proposal.getCreationDateAsDate(), proposal.getTxId(), inPhase, txInPastCycle, unconfirmedOrNonBsqTx);
                    }
                } else {
                    log.warn("We received a remove request outside the PROPOSAL phase. " +
                                    "Proposal creation date={}, proposal.txId={}, current blockHeight={}",
                            proposal.getCreationDateAsDate(), proposal.getTxId(), daoStateService.getChainHeight());
                }
            }
        });

        tempProposals.retainAll(tempProposalsWithUpdates);
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload, boolean fromBroadcastMessage) {
        if (persistableNetworkPayload instanceof ProposalPayload) {
            ProposalPayload proposalPayload = (ProposalPayload) persistableNetworkPayload;
            if (!proposalPayloads.contains(proposalPayload)) {
                Proposal proposal = proposalPayload.getProposal();

                // We don't validate phase and cycle as we might receive proposals from other cycles or phases at startup.
                // Beside that we might receive payloads we requested at the vote result phase in case we missed some
                // payloads. We prefer here resilience over protection against late publishing attacks.

                // We only validate in case the blocks are parsed as otherwise some validators like param validator
                // might fail as Dao state is not complete.
                if (!daoStateService.isParseBlockChainComplete() ||
                        validatorProvider.getValidator(proposal).areDataFieldsValid(proposal)) {
                    if (fromBroadcastMessage) {
                        log.info("We received a ProposalPayload and store it to our appendOnlyStoreList. proposalTxId={}",
                                proposal.getTxId());
                    }
                    proposalPayloads.add(proposalPayload);
                } else {
                    log.warn("We received a invalid append-only proposal from the P2P network. " +
                                    "Proposal={}, blockHeight={}",
                            proposal, daoStateService.getChainHeight());
                }
            }
        }
    }
}
