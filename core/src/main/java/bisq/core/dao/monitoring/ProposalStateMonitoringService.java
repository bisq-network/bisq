/*
 * This file is part of Bisq.
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

package bisq.core.dao.monitoring;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.MyProposalList;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.monitoring.model.ProposalStateBlock;
import bisq.core.dao.monitoring.model.ProposalStateHash;
import bisq.core.dao.monitoring.network.ProposalStateNetworkService;
import bisq.core.dao.monitoring.network.messages.GetProposalStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.NewProposalStateHashMessage;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.UserThread;
import bisq.common.crypto.Hash;

import javax.inject.Inject;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Monitors the Proposal P2P network payloads with using a hash of a sorted list of Proposals from one cycle and
 * make it accessible to the network so we can detect quickly if any consensus issue arises.
 * We create that hash at the first block of the BlindVote phase. There is one hash created per cycle.
 * The hash contains the hash of the previous block so we can ensure the validity of the whole history by
 * comparing the last block.
 *
 * We request the state from the connected seed nodes after batch processing of BSQ is complete as well as we start
 * to listen for broadcast messages from our peers about dao state of new blocks.
 *
 * We do NOT persist that chain of hashes as there is only one per cycle and the performance costs are very low.
 */
@Slf4j
public class ProposalStateMonitoringService implements DaoSetupService, DaoStateListener, ProposalStateNetworkService.Listener<NewProposalStateHashMessage, GetProposalStateHashesRequest, ProposalStateHash> {
    public interface Listener {
        void onProposalStateBlockChainChanged();
    }

    private final DaoStateService daoStateService;
    private final ProposalStateNetworkService proposalStateNetworkService;
    private final GenesisTxInfo genesisTxInfo;
    private final PeriodService periodService;
    private final ProposalService proposalService;
    private final Set<String> seedNodeAddresses;


    @Getter
    private final LinkedList<ProposalStateBlock> proposalStateBlockChain = new LinkedList<>();
    @Getter
    private final LinkedList<ProposalStateHash> proposalStateHashChain = new LinkedList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    @Getter
    private boolean isInConflictWithNonSeedNode;
    @Getter
    private boolean isInConflictWithSeedNode;
    private boolean parseBlockChainComplete;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalStateMonitoringService(DaoStateService daoStateService,
                                          ProposalStateNetworkService proposalStateNetworkService,
                                          GenesisTxInfo genesisTxInfo,
                                          PeriodService periodService,
                                          ProposalService proposalService,
                                          SeedNodeRepository seedNodeRepository) {
        this.daoStateService = daoStateService;
        this.proposalStateNetworkService = proposalStateNetworkService;
        this.genesisTxInfo = genesisTxInfo;
        this.periodService = periodService;
        this.proposalService = proposalService;
        seedNodeAddresses = seedNodeRepository.getSeedNodeAddresses().stream()
                .map(NodeAddress::getFullAddress)
                .collect(Collectors.toSet());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
        proposalStateNetworkService.addListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("Duplicates")
    public void onDaoStateChanged(Block block) {
        int blockHeight = block.getHeight();
        int genesisBlockHeight = genesisTxInfo.getGenesisBlockHeight();

        boolean hashChainUpdated = false;
        if (proposalStateBlockChain.isEmpty() && blockHeight > genesisBlockHeight) {
            // Takes about 150 ms for dao testnet data
            long ts = System.currentTimeMillis();
            for (int i = genesisBlockHeight; i < blockHeight; i++) {
                boolean isHashChainUpdated = maybeUpdateHashChain(i);
                if (isHashChainUpdated) {
                    hashChainUpdated = true;
                }
            }
            if (hashChainUpdated) {
                log.info("updateHashChain for {} blocks took {} ms",
                        blockHeight - genesisBlockHeight,
                        System.currentTimeMillis() - ts);
            }
        }
        long ts = System.currentTimeMillis();
        boolean updated = maybeUpdateHashChain(blockHeight);
        if (updated) {
            log.info("updateHashChain for block {} took {} ms",
                    blockHeight,
                    System.currentTimeMillis() - ts);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void onParseBlockChainComplete() {
        parseBlockChainComplete = true;
        proposalStateNetworkService.addListeners();

        // We wait for processing messages until we have completed batch processing

        // We request data from last 5 cycles. We ignore possible duration changes done by voting.
        // period is arbitrary anyway...
        Cycle currentCycle = periodService.getCurrentCycle();
        checkNotNull(currentCycle, "currentCycle must not be null");
        int fromHeight = Math.max(genesisTxInfo.getGenesisBlockHeight(), daoStateService.getChainHeight() - currentCycle.getDuration() * 5);

        proposalStateNetworkService.requestHashesFromAllConnectedSeedNodes(fromHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateNetworkService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewStateHashMessage(NewProposalStateHashMessage newStateHashMessage, Connection connection) {
        if (newStateHashMessage.getStateHash().getHeight() <= daoStateService.getChainHeight()) {
            processPeersProposalStateHash(newStateHashMessage.getStateHash(), connection.getPeersNodeAddressOptional(), true);
        }
    }

    @Override
    public void onGetStateHashRequest(Connection connection, GetProposalStateHashesRequest getStateHashRequest) {
        int fromHeight = getStateHashRequest.getHeight();
        List<ProposalStateHash> proposalStateHashes = proposalStateBlockChain.stream()
                .filter(e -> e.getHeight() >= fromHeight)
                .map(ProposalStateBlock::getMyStateHash)
                .collect(Collectors.toList());
        proposalStateNetworkService.sendGetStateHashesResponse(connection, getStateHashRequest.getNonce(), proposalStateHashes);
    }

    @Override
    public void onPeersStateHashes(List<ProposalStateHash> stateHashes, Optional<NodeAddress> peersNodeAddress) {
        AtomicBoolean hasChanged = new AtomicBoolean(false);
        stateHashes.forEach(daoStateHash -> {
            boolean changed = processPeersProposalStateHash(daoStateHash, peersNodeAddress, false);
            if (changed) {
                hasChanged.set(true);
            }
        });

        if (hasChanged.get()) {
            listeners.forEach(Listener::onProposalStateBlockChainChanged);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestHashesFromGenesisBlockHeight(String peersAddress) {
        proposalStateNetworkService.requestHashes(genesisTxInfo.getGenesisBlockHeight(), peersAddress);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean maybeUpdateHashChain(int blockHeight) {
        // We use first block in blind vote phase to create the hash of our proposals. We prefer to wait as long as
        // possible to increase the chance that we have received all proposals.
        if (!isFirstBlockOfBlindVotePhase(blockHeight)) {
            return false;
        }

        periodService.getCycle(blockHeight).ifPresent(cycle -> {
            List<Proposal> proposals = proposalService.getValidatedProposals().stream()
                    .filter(e -> e.getTxId() != null)
                    .filter(e -> periodService.isTxInPhaseAndCycle(e.getTxId(), DaoPhase.Phase.PROPOSAL, blockHeight))
                    .sorted(Comparator.comparing(Proposal::getTxId))
                    .collect(Collectors.toList());

            // We use MyProposalList to get the serialized bytes from the proposals list
            byte[] serializedProposals = new MyProposalList(proposals).toProtoMessage().toByteArray();

            byte[] prevHash;
            if (proposalStateBlockChain.isEmpty()) {
                prevHash = new byte[0];
            } else {
                prevHash = proposalStateBlockChain.getLast().getHash();
            }
            byte[] combined = ArrayUtils.addAll(prevHash, serializedProposals);
            byte[] hash = Hash.getSha256Ripemd160hash(combined);
            ProposalStateHash myProposalStateHash = new ProposalStateHash(blockHeight, hash, proposals.size());
            ProposalStateBlock proposalStateBlock = new ProposalStateBlock(myProposalStateHash);
            proposalStateBlockChain.add(proposalStateBlock);
            proposalStateHashChain.add(myProposalStateHash);

            // We only broadcast after parsing of blockchain is complete
            if (parseBlockChainComplete) {
                // We notify listeners only after batch processing to avoid performance issues at UI code
                listeners.forEach(Listener::onProposalStateBlockChainChanged);

                // We delay broadcast to give peers enough time to have received the block.
                // Otherwise they would ignore our data if received block is in future to their local blockchain.
                int delayInSec = 5 + new Random().nextInt(10);
                UserThread.runAfter(() -> proposalStateNetworkService.broadcastMyStateHash(myProposalStateHash), delayInSec);
            }
        });
        return true;
    }

    private boolean processPeersProposalStateHash(ProposalStateHash proposalStateHash, Optional<NodeAddress> peersNodeAddress, boolean notifyListeners) {
        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicBoolean inConflictWithNonSeedNode = new AtomicBoolean(this.isInConflictWithNonSeedNode);
        AtomicBoolean inConflictWithSeedNode = new AtomicBoolean(this.isInConflictWithSeedNode);
        StringBuilder sb = new StringBuilder();
        proposalStateBlockChain.stream()
                .filter(e -> e.getHeight() == proposalStateHash.getHeight()).findAny()
                .ifPresent(daoStateBlock -> {
                    String peersNodeAddressAsString = peersNodeAddress.map(NodeAddress::getFullAddress)
                            .orElseGet(() -> "Unknown peer " + new Random().nextInt(10000));
                    daoStateBlock.putInPeersMap(peersNodeAddressAsString, proposalStateHash);
                    if (!daoStateBlock.getMyStateHash().hasEqualHash(proposalStateHash)) {
                        daoStateBlock.putInConflictMap(peersNodeAddressAsString, proposalStateHash);
                        if (seedNodeAddresses.contains(peersNodeAddressAsString)) {
                            inConflictWithSeedNode.set(true);
                        } else {
                            inConflictWithNonSeedNode.set(true);
                        }
                        sb.append("We received a block hash from peer ")
                                .append(peersNodeAddressAsString)
                                .append(" which conflicts with our block hash.\n")
                                .append("my proposalStateHash=")
                                .append(daoStateBlock.getMyStateHash())
                                .append("\npeers proposalStateHash=")
                                .append(proposalStateHash);
                    }
                    changed.set(true);
                });

        this.isInConflictWithNonSeedNode = inConflictWithNonSeedNode.get();
        this.isInConflictWithSeedNode = inConflictWithSeedNode.get();

        String conflictMsg = sb.toString();
        if (!conflictMsg.isEmpty()) {
            if (this.isInConflictWithSeedNode)
                log.warn("Conflict with seed nodes: {}", conflictMsg);
            else if (this.isInConflictWithNonSeedNode)
                log.info("Conflict with non-seed nodes: {}", conflictMsg);
        }

        if (notifyListeners && changed.get()) {
            listeners.forEach(Listener::onProposalStateBlockChainChanged);
        }

        return changed.get();
    }

    private boolean isFirstBlockOfBlindVotePhase(int blockHeight) {
        return blockHeight == periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.BLIND_VOTE);
    }
}
