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
import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.blindvote.MyBlindVoteList;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.monitoring.model.BlindVoteStateBlock;
import bisq.core.dao.monitoring.model.BlindVoteStateHash;
import bisq.core.dao.monitoring.network.BlindVoteStateNetworkService;
import bisq.core.dao.monitoring.network.messages.GetBlindVoteStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.NewBlindVoteStateHashMessage;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;

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
 * Monitors the BlindVote P2P network payloads with using a hash of a sorted list of BlindVotes from one cycle and
 * make it accessible to the network so we can detect quickly if any consensus issue arises.
 * We create that hash at the first block of the VoteReveal phase. There is one hash created per cycle.
 * The hash contains the hash of the previous block so we can ensure the validity of the whole history by
 * comparing the last block.
 *
 * We request the state from the connected seed nodes after batch processing of BSQ is complete as well as we start
 * to listen for broadcast messages from our peers about dao state of new blocks.
 *
 * We do NOT persist that chain of hashes as there is only one per cycle and the performance costs are very low.
 */
@Slf4j
public class BlindVoteStateMonitoringService implements DaoSetupService, DaoStateListener, BlindVoteStateNetworkService.Listener<NewBlindVoteStateHashMessage, GetBlindVoteStateHashesRequest, BlindVoteStateHash> {
    public interface Listener {
        void onBlindVoteStateBlockChainChanged();
    }

    private final DaoStateService daoStateService;
    private final BlindVoteStateNetworkService blindVoteStateNetworkService;
    private final GenesisTxInfo genesisTxInfo;
    private final PeriodService periodService;
    private final BlindVoteListService blindVoteListService;
    private final Set<String> seedNodeAddresses;

    @Getter
    private final LinkedList<BlindVoteStateBlock> blindVoteStateBlockChain = new LinkedList<>();
    @Getter
    private final LinkedList<BlindVoteStateHash> blindVoteStateHashChain = new LinkedList<>();
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
    public BlindVoteStateMonitoringService(DaoStateService daoStateService,
                                           BlindVoteStateNetworkService blindVoteStateNetworkService,
                                           GenesisTxInfo genesisTxInfo,
                                           PeriodService periodService,
                                           BlindVoteListService blindVoteListService,
                                           SeedNodeRepository seedNodeRepository) {
        this.daoStateService = daoStateService;
        this.blindVoteStateNetworkService = blindVoteStateNetworkService;
        this.genesisTxInfo = genesisTxInfo;
        this.periodService = periodService;
        this.blindVoteListService = blindVoteListService;
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
        blindVoteStateNetworkService.addListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("Duplicates")
    @Override
    public void onDaoStateChanged(Block block) {
        int blockHeight = block.getHeight();

        int genesisBlockHeight = genesisTxInfo.getGenesisBlockHeight();

        if (blindVoteStateBlockChain.isEmpty() && blockHeight > genesisBlockHeight) {
            // Takes about 150 ms for dao testnet data
            long ts = System.currentTimeMillis();
            for (int i = genesisBlockHeight; i < blockHeight; i++) {
                maybeUpdateHashChain(i);
            }
            if (!blindVoteStateBlockChain.isEmpty()) {
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
        blindVoteStateNetworkService.addListeners();

        // We wait for processing messages until we have completed batch processing

        // We request data from last 5 cycles. We ignore possible duration changes done by voting.
        // period is arbitrary anyway...
        Cycle currentCycle = periodService.getCurrentCycle();
        checkNotNull(currentCycle, "currentCycle must not be null");
        int fromHeight = Math.max(genesisTxInfo.getGenesisBlockHeight(), daoStateService.getChainHeight() - currentCycle.getDuration() * 5);

        blindVoteStateNetworkService.requestHashesFromAllConnectedSeedNodes(fromHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateNetworkService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewStateHashMessage(NewBlindVoteStateHashMessage newStateHashMessage, Connection connection) {
        if (newStateHashMessage.getStateHash().getHeight() <= daoStateService.getChainHeight()) {
            processPeersBlindVoteStateHash(newStateHashMessage.getStateHash(), connection.getPeersNodeAddressOptional(), true);
        }
    }

    @Override
    public void onGetStateHashRequest(Connection connection, GetBlindVoteStateHashesRequest getStateHashRequest) {
        int fromHeight = getStateHashRequest.getHeight();
        List<BlindVoteStateHash> blindVoteStateHashes = blindVoteStateBlockChain.stream()
                .filter(e -> e.getHeight() >= fromHeight)
                .map(BlindVoteStateBlock::getMyStateHash)
                .collect(Collectors.toList());
        blindVoteStateNetworkService.sendGetStateHashesResponse(connection, getStateHashRequest.getNonce(), blindVoteStateHashes);
    }

    @Override
    public void onPeersStateHashes(List<BlindVoteStateHash> stateHashes, Optional<NodeAddress> peersNodeAddress) {
        AtomicBoolean hasChanged = new AtomicBoolean(false);
        stateHashes.forEach(daoStateHash -> {
            boolean changed = processPeersBlindVoteStateHash(daoStateHash, peersNodeAddress, false);
            if (changed) {
                hasChanged.set(true);
            }
        });

        if (hasChanged.get()) {
            listeners.forEach(Listener::onBlindVoteStateBlockChainChanged);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestHashesFromGenesisBlockHeight(String peersAddress) {
        blindVoteStateNetworkService.requestHashes(genesisTxInfo.getGenesisBlockHeight(), peersAddress);
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
        // We use first block in blind vote phase to create the hash of our blindVotes. We prefer to wait as long as
        // possible to increase the chance that we have received all blindVotes.
        if (!isFirstBlockOfBlindVotePhase(blockHeight)) {
            return false;
        }

        periodService.getCycle(blockHeight).ifPresent(cycle -> {
            List<BlindVote> blindVotes = blindVoteListService.getConfirmedBlindVotes().stream()
                    .filter(e -> e.getTxId() != null)
                    .filter(e -> periodService.isTxInPhaseAndCycle(e.getTxId(), DaoPhase.Phase.BLIND_VOTE, blockHeight))
                    .sorted(Comparator.comparing(BlindVote::getTxId))
                    .collect(Collectors.toList());

            // We use MyBlindVoteList to get the serialized bytes from the blindVotes list
            byte[] serializedBlindVotes = new MyBlindVoteList(blindVotes).toProtoMessage().toByteArray();

            byte[] prevHash;
            if (blindVoteStateBlockChain.isEmpty()) {
                prevHash = new byte[0];
            } else {
                prevHash = blindVoteStateBlockChain.getLast().getHash();
            }
            byte[] combined = ArrayUtils.addAll(prevHash, serializedBlindVotes);
            byte[] hash = Hash.getSha256Ripemd160hash(combined);

            BlindVoteStateHash myBlindVoteStateHash = new BlindVoteStateHash(blockHeight, hash, blindVotes.size());
            BlindVoteStateBlock blindVoteStateBlock = new BlindVoteStateBlock(myBlindVoteStateHash);
            blindVoteStateBlockChain.add(blindVoteStateBlock);
            blindVoteStateHashChain.add(myBlindVoteStateHash);

            // We only broadcast after parsing of blockchain is complete
            if (parseBlockChainComplete) {
                // We notify listeners only after batch processing to avoid performance issues at UI code
                listeners.forEach(Listener::onBlindVoteStateBlockChainChanged);

                // We delay broadcast to give peers enough time to have received the block.
                // Otherwise they would ignore our data if received block is in future to their local blockchain.
                int delayInSec = 5 + new Random().nextInt(10);
                UserThread.runAfter(() -> blindVoteStateNetworkService.broadcastMyStateHash(myBlindVoteStateHash), delayInSec);
            }
        });
        return true;
    }

    private boolean processPeersBlindVoteStateHash(BlindVoteStateHash blindVoteStateHash,
                                                   Optional<NodeAddress> peersNodeAddress,
                                                   boolean notifyListeners) {
        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicBoolean inConflictWithNonSeedNode = new AtomicBoolean(this.isInConflictWithNonSeedNode);
        AtomicBoolean inConflictWithSeedNode = new AtomicBoolean(this.isInConflictWithSeedNode);
        StringBuilder sb = new StringBuilder();
        blindVoteStateBlockChain.stream()
                .filter(e -> e.getHeight() == blindVoteStateHash.getHeight()).findAny()
                .ifPresent(daoStateBlock -> {
                    String peersNodeAddressAsString = peersNodeAddress.map(NodeAddress::getFullAddress)
                            .orElseGet(() -> "Unknown peer " + new Random().nextInt(10000));
                    daoStateBlock.putInPeersMap(peersNodeAddressAsString, blindVoteStateHash);
                    if (!daoStateBlock.getMyStateHash().hasEqualHash(blindVoteStateHash)) {
                        daoStateBlock.putInConflictMap(peersNodeAddressAsString, blindVoteStateHash);
                        if (seedNodeAddresses.contains(peersNodeAddressAsString)) {
                            inConflictWithSeedNode.set(true);
                        } else {
                            inConflictWithNonSeedNode.set(true);
                        }

                        sb.append("We received a block hash from peer ")
                                .append(peersNodeAddressAsString)
                                .append(" which conflicts with our block hash.\n")
                                .append("my blindVoteStateHash=")
                                .append(daoStateBlock.getMyStateHash())
                                .append("\npeers blindVoteStateHash=")
                                .append(blindVoteStateHash);
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
            listeners.forEach(Listener::onBlindVoteStateBlockChainChanged);
        }

        return changed.get();
    }

    private boolean isFirstBlockOfBlindVotePhase(int blockHeight) {
        return blockHeight == periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.VOTE_REVEAL);
    }
}
