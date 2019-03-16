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
import bisq.core.dao.monitoring.model.DaoStateBlock;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.network.DaoStateNetworkService;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.NewDaoStateHashMessage;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;

import bisq.common.UserThread;
import bisq.common.crypto.Hash;

import javax.inject.Inject;

import org.apache.commons.lang3.ArrayUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Monitors the DaoState with using a hash fo the complete daoState and make it accessible to the network
 * so we can detect quickly if any consensus issue arise.
 * We create that hash after parsing and processing of a block is completed. There is one hash created per block.
 * The hash contains the hash of the previous block so we can ensure the validity of the whole history by
 * comparing the last block.
 *
 * We request the state from the connected seed nodes after batch processing of BSQ is complete as well as we start
 * to listen for broadcast messages from our peers about dao state of new blocks. It could be that the received dao
 * state from the peers is already covering the next block we have not received yet. So we only take data in account
 * which are inside the block height we have already. To avoid such race conditions we delay the broadcasting of our
 * state to the peers to not get ignored it in case they have not received the block yet.
 *
 * We do persist that chain of hashes with the snapshot.
 */
@Slf4j
public class DaoStateMonitoringService implements DaoSetupService, DaoStateListener,
        DaoStateNetworkService.Listener<NewDaoStateHashMessage, GetDaoStateHashesRequest, DaoStateHash> {

    public interface Listener {
        void onChangeAfterBatchProcessing();
    }

    private final DaoStateService daoStateService;
    private final DaoStateNetworkService daoStateNetworkService;
    private final GenesisTxInfo genesisTxInfo;

    @Getter
    private final LinkedList<DaoStateBlock> daoStateBlockChain = new LinkedList<>();
    @Getter
    private final LinkedList<DaoStateHash> daoStateHashChain = new LinkedList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private boolean parseBlockChainComplete;
    @Getter
    private boolean isInConflict;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateMonitoringService(DaoStateService daoStateService,
                                     DaoStateNetworkService daoStateNetworkService,
                                     GenesisTxInfo genesisTxInfo) {
        this.daoStateService = daoStateService;
        this.daoStateNetworkService = daoStateNetworkService;
        this.genesisTxInfo = genesisTxInfo;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
        daoStateNetworkService.addListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We do not use onDaoStateChanged but let the DaoEventCoordinator call createHashFromBlock to ensure the
    // correct order of execution.

    @Override
    public void onParseBlockChainComplete() {
        parseBlockChainComplete = true;
        daoStateNetworkService.addListeners();

        // We wait for processing messages until we have completed batch processing
        int fromHeight = daoStateService.getChainHeight() - 10;
        daoStateNetworkService.requestHashesFromAllConnectedSeedNodes(fromHeight);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateNetworkService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewStateHashMessage(NewDaoStateHashMessage newStateHashMessage, Connection connection) {
        if (newStateHashMessage.getStateHash().getHeight() <= daoStateService.getChainHeight()) {
            processPeersDaoStateHash(newStateHashMessage.getStateHash(), connection.getPeersNodeAddressOptional(), true);
        }
    }

    @Override
    public void onGetStateHashRequest(Connection connection, GetDaoStateHashesRequest getStateHashRequest) {
        int fromHeight = getStateHashRequest.getHeight();
        List<DaoStateHash> daoStateHashes = daoStateBlockChain.stream()
                .filter(e -> e.getHeight() >= fromHeight)
                .map(DaoStateBlock::getMyStateHash)
                .collect(Collectors.toList());
        daoStateNetworkService.sendGetStateHashesResponse(connection, getStateHashRequest.getNonce(), daoStateHashes);
    }

    @Override
    public void onPeersStateHashes(List<DaoStateHash> stateHashes, Optional<NodeAddress> peersNodeAddress) {
        AtomicBoolean hasChanged = new AtomicBoolean(false);

        stateHashes.forEach(daoStateHash -> {
            boolean changed = processPeersDaoStateHash(daoStateHash, peersNodeAddress, false);
            if (changed) {
                hasChanged.set(true);
            }
        });

        if (hasChanged.get()) {
            listeners.forEach(Listener::onChangeAfterBatchProcessing);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void createHashFromBlock(Block block) {
        updateHashChain(block);
    }

    public void requestHashesFromGenesisBlockHeight(String peersAddress) {
        daoStateNetworkService.requestHashes(genesisTxInfo.getGenesisBlockHeight(), peersAddress);
    }

    public void applySnapshot(LinkedList<DaoStateHash> persistedDaoStateHashChain) {
        // We could got a reset from a reorg, so we clear all and start over from the genesis block.
        daoStateHashChain.clear();
        daoStateBlockChain.clear();
        daoStateNetworkService.reset();

        if (!persistedDaoStateHashChain.isEmpty()) {
            log.info("Apply snapshot with {} daoStateHashes. Last daoStateHash={}",
                    persistedDaoStateHashChain.size(), persistedDaoStateHashChain.getLast());
        }
        daoStateHashChain.addAll(persistedDaoStateHashChain);
        daoStateHashChain.forEach(e -> daoStateBlockChain.add(new DaoStateBlock(e)));
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

    private void updateHashChain(Block block) {
        byte[] prevHash;
        int height = block.getHeight();
        if (daoStateBlockChain.isEmpty()) {
            // Only at genesis we allow an empty prevHash
            if (height == genesisTxInfo.getGenesisBlockHeight()) {
                prevHash = new byte[0];
            } else {
                log.warn("DaoStateBlockchain is empty but we received the block which was not the genesis block. " +
                        "We stop execution here.");
                return;
            }
        } else {
            checkArgument(height == daoStateBlockChain.getLast().getHeight() + 1,
                    "New block must be 1 block above previous block. height={}, " +
                            "daoStateBlockChain.getLast().getHeight()={}",
                    height, daoStateBlockChain.getLast().getHeight());
            prevHash = daoStateBlockChain.getLast().getHash();
        }
        byte[] stateHash = daoStateService.getSerializedDaoState();
        // We include the prev. hash in our new hash so we can be sure that if one hash is matching all the past would
        // match as well.
        byte[] combined = ArrayUtils.addAll(prevHash, stateHash);
        byte[] hash = Hash.getSha256Ripemd160hash(combined);

        DaoStateHash myDaoStateHash = new DaoStateHash(height, hash, prevHash);
        DaoStateBlock daoStateBlock = new DaoStateBlock(myDaoStateHash);
        daoStateBlockChain.add(daoStateBlock);
        daoStateHashChain.add(myDaoStateHash);

        // We only broadcast after parsing of blockchain is complete
        if (parseBlockChainComplete) {
            // We notify listeners only after batch processing to avoid performance issues at UI code
            listeners.forEach(Listener::onChangeAfterBatchProcessing);

            // We delay broadcast to give peers enough time to have received the block.
            // Otherwise they would ignore our data if received block is in future to their local blockchain.
            int delayInSec = 5 + new Random().nextInt(10);
            UserThread.runAfter(() -> daoStateNetworkService.broadcastMyStateHash(myDaoStateHash), delayInSec);
        }
    }

    private boolean processPeersDaoStateHash(DaoStateHash daoStateHash, Optional<NodeAddress> peersNodeAddress, boolean notifyListeners) {
        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicBoolean isInConflict = new AtomicBoolean(this.isInConflict);
        StringBuilder sb = new StringBuilder();
        daoStateBlockChain.stream()
                .filter(e -> e.getHeight() == daoStateHash.getHeight()).findAny()
                .ifPresent(daoStateBlock -> {
                    String peersNodeAddressAsString = peersNodeAddress.map(NodeAddress::getFullAddress)
                            .orElseGet(() -> "Unknown peer " + new Random().nextInt(10000));
                    daoStateBlock.putInPeersMap(peersNodeAddressAsString, daoStateHash);
                    if (!daoStateBlock.getMyStateHash().hasEqualHash(daoStateHash)) {
                        daoStateBlock.putInConflictMap(peersNodeAddressAsString, daoStateHash);
                        isInConflict.set(true);
                        sb.append("We received a block hash from peer ")
                                .append(peersNodeAddressAsString)
                                .append(" which conflicts with our block hash.\n")
                                .append("my daoStateHash=")
                                .append(daoStateBlock.getMyStateHash())
                                .append("\npeers daoStateHash=")
                                .append(daoStateHash);
                    }
                    changed.set(true);
                });

        this.isInConflict = isInConflict.get();

        String conflictMsg = sb.toString();
        if (this.isInConflict && !conflictMsg.isEmpty()) {
            log.warn(conflictMsg);
        }

        if (notifyListeners && changed.get()) {
            listeners.forEach(Listener::onChangeAfterBatchProcessing);
        }

        return changed.get();
    }
}
