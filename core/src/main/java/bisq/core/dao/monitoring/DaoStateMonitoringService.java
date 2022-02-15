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
import bisq.core.dao.monitoring.model.UtxoMismatch;
import bisq.core.dao.monitoring.network.Checkpoint;
import bisq.core.dao.monitoring.network.DaoStateNetworkService;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.NewDaoStateHashMessage;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.crypto.Hash;
import bisq.common.file.FileUtil;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.ArrayUtils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Monitors the DaoState by using a hash for the complete daoState and make it accessible to the network
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
        void onDaoStateHashesChanged();

        void onCheckpointFail();
    }

    private final DaoStateService daoStateService;
    private final DaoStateNetworkService daoStateNetworkService;
    private final GenesisTxInfo genesisTxInfo;
    private final Set<String> seedNodeAddresses;

    @Getter
    private final LinkedList<DaoStateBlock> daoStateBlockChain = new LinkedList<>();
    @Getter
    private final LinkedList<DaoStateHash> daoStateHashChain = new LinkedList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private boolean parseBlockChainComplete;
    @Getter
    private boolean isInConflictWithNonSeedNode;
    @Getter
    private boolean isInConflictWithSeedNode;
    @Getter
    private boolean daoStateBlockChainNotConnecting;
    @Getter
    private final ObservableList<UtxoMismatch> utxoMismatches = FXCollections.observableArrayList();

    private final List<Checkpoint> checkpoints = Arrays.asList(
            new Checkpoint(586920, Utilities.decodeFromHex("523aaad4e760f6ac6196fec1b3ec9a2f42e5b272"))
    );
    private boolean checkpointFailed;
    private final boolean ignoreDevMsg;
    private int numCalls;
    private long accumulatedDuration;

    private final Preferences preferences;
    private final File storageDir;
    @Nullable
    private Runnable createSnapshotHandler;
    // Lookup map
    private final Map<Integer, DaoStateBlock> daoStateBlockByHeight = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateMonitoringService(DaoStateService daoStateService,
                                     DaoStateNetworkService daoStateNetworkService,
                                     GenesisTxInfo genesisTxInfo,
                                     SeedNodeRepository seedNodeRepository,
                                     Preferences preferences,
                                     @Named(Config.STORAGE_DIR) File storageDir,
                                     @Named(Config.IGNORE_DEV_MSG) boolean ignoreDevMsg) {
        this.daoStateService = daoStateService;
        this.daoStateNetworkService = daoStateNetworkService;
        this.genesisTxInfo = genesisTxInfo;
        this.preferences = preferences;
        this.storageDir = storageDir;
        this.ignoreDevMsg = ignoreDevMsg;
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
        daoStateNetworkService.addListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockChainComplete() {
        parseBlockChainComplete = true;
        daoStateService.getLastBlock().ifPresent(this::checkUtxos);

        daoStateNetworkService.addListeners();

        // We take either the height of the previous hashBlock we have or 10 blocks below the chain tip.
        int nextBlockHeight = daoStateBlockChain.isEmpty() ?
                genesisTxInfo.getGenesisBlockHeight() :
                daoStateBlockChain.getLast().getHeight() + 1;
        int past10 = daoStateService.getChainHeight() - 10;
        int fromHeight = Math.min(nextBlockHeight, past10);
        daoStateNetworkService.requestHashesFromAllConnectedSeedNodes(fromHeight);

        if (!ignoreDevMsg) {
            verifyCheckpoints();
        }

        log.info("ParseBlockChainComplete: Accumulated updateHashChain() calls for {} block took {} ms " +
                        "({} ms in average / block)",
                numCalls,
                accumulatedDuration,
                (int) ((double) accumulatedDuration / (double) numCalls));
    }

    @Override
    public void onDaoStateChanged(Block block) {
        // During syncing we do not call checkUtxos as its a bit slow (about 4 ms)
        if (parseBlockChainComplete) {
            checkUtxos(block);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateNetworkService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewStateHashMessage(NewDaoStateHashMessage newStateHashMessage, Connection connection) {
        // Called when receiving NewDaoStateHashMessages from peers after a new block
        DaoStateHash peersDaoStateHash = newStateHashMessage.getStateHash();
        if (peersDaoStateHash.getHeight() <= daoStateService.getChainHeight()) {
            putInPeersMapAndCheckForConflicts(getPeersAddress(connection.getPeersNodeAddressOptional()), peersDaoStateHash);
            listeners.forEach(Listener::onDaoStateHashesChanged);
        }
    }

    @Override
    public void onPeersStateHashes(List<DaoStateHash> stateHashes, Optional<NodeAddress> peersNodeAddress) {
        // Called when receiving GetDaoStateHashesResponse from seed nodes
        processPeersDaoStateHashes(stateHashes, peersNodeAddress);
        listeners.forEach(Listener::onDaoStateHashesChanged);
        if (createSnapshotHandler != null) {
            createSnapshotHandler.run();
            // As we get called multiple times from hashes of diff. seed nodes we want to avoid to
            // call our handler multiple times.
            createSnapshotHandler = null;
        }
    }

    @Override
    public void onGetStateHashRequest(Connection connection, GetDaoStateHashesRequest getStateHashRequest) {
        int fromHeight = getStateHashRequest.getHeight();
        List<DaoStateHash> daoStateHashes = daoStateHashChain.stream()
                .filter(e -> e.getHeight() >= fromHeight)
                .collect(Collectors.toList());
        daoStateNetworkService.sendGetStateHashesResponse(connection, getStateHashRequest.getNonce(), daoStateHashes);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void createHashFromBlock(Block block) {
        createDaoStateBlock(block);
        if (parseBlockChainComplete) {
            // We notify listeners only after batch processing to avoid performance issues at UI code
            listeners.forEach(Listener::onDaoStateHashesChanged);
        }
    }

    public void requestHashesFromGenesisBlockHeight(String peersAddress) {
        daoStateNetworkService.requestHashes(genesisTxInfo.getGenesisBlockHeight(), peersAddress);
    }

    public void applySnapshot(LinkedList<DaoStateHash> persistedDaoStateHashChain) {
        // We could get a reset from a reorg, so we clear all and start over from the genesis block.
        daoStateHashChain.clear();
        daoStateBlockChain.clear();
        daoStateBlockByHeight.clear();
        daoStateNetworkService.reset();

        if (!persistedDaoStateHashChain.isEmpty()) {
            log.info("Apply snapshot with {} daoStateHashes. Last daoStateHash={}",
                    persistedDaoStateHashChain.size(), persistedDaoStateHashChain.getLast());
        }
        daoStateHashChain.addAll(persistedDaoStateHashChain);
        daoStateHashChain.forEach(daoStateHash -> {
            DaoStateBlock daoStateBlock = new DaoStateBlock(daoStateHash);
            daoStateBlockChain.add(daoStateBlock);
            daoStateBlockByHeight.put(daoStateHash.getHeight(), daoStateBlock);
        });
    }

    public void setCreateSnapshotHandler(Runnable handler) {
        createSnapshotHandler = handler;
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

    private Optional<DaoStateBlock> createDaoStateBlock(Block block) {
        long ts = System.currentTimeMillis();
        byte[] prevHash;
        int height = block.getHeight();
        if (daoStateBlockChain.isEmpty()) {
            // Only at genesis we allow an empty prevHash
            if (height == genesisTxInfo.getGenesisBlockHeight()) {
                prevHash = new byte[0];
            } else {
                log.warn("DaoStateBlockchain is empty but we received the block which was not the genesis block. " +
                        "We stop execution here.");
                daoStateBlockChainNotConnecting = true;
                listeners.forEach(Listener::onDaoStateHashesChanged);
                return Optional.empty();
            }
        } else {
            DaoStateBlock last = daoStateBlockChain.getLast();
            int heightOfLastBlock = last.getHeight();
            if (height == heightOfLastBlock + 1) {
                prevHash = last.getHash();
            } else {
                log.warn("New block must be 1 block above previous block. height={}, " +
                                "daoStateBlockChain.getLast().getHeight()={}",
                        height, heightOfLastBlock);
                daoStateBlockChainNotConnecting = true;
                listeners.forEach(Listener::onDaoStateHashesChanged);
                return Optional.empty();
            }
        }

        byte[] stateAsBytes = daoStateService.getSerializedStateForHashChain();
        // We include the prev. hash in our new hash so we can be sure that if one hash is matching all the past would
        // match as well.
        byte[] combined = ArrayUtils.addAll(prevHash, stateAsBytes);
        byte[] hash = Hash.getSha256Ripemd160hash(combined);

        DaoStateHash myDaoStateHash = new DaoStateHash(height, hash, true);
        DaoStateBlock daoStateBlock = new DaoStateBlock(myDaoStateHash);
        daoStateBlockChain.add(daoStateBlock);
        daoStateBlockByHeight.put(height, daoStateBlock);
        daoStateHashChain.add(myDaoStateHash);

        // We only broadcast after parsing of blockchain is complete
        if (parseBlockChainComplete) {
            // We delay broadcast to give peers enough time to have received the block.
            // Otherwise they would ignore our data if received block is in future to their local blockchain.
            int delayInSec = 5 + new Random().nextInt(10);
            if (Config.baseCurrencyNetwork().isRegtest()) {
                delayInSec = 1;
            }
            UserThread.runAfter(() -> daoStateNetworkService.broadcastMyStateHash(myDaoStateHash), delayInSec);
        }
        long duration = System.currentTimeMillis() - ts;
        // We don't want to spam the output. We log accumulated time after parsing is completed.
        log.trace("updateHashChain for block {} took {} ms",
                block.getHeight(),
                duration);
        accumulatedDuration += duration;
        numCalls++;
        return Optional.of(daoStateBlock);
    }

    private void processPeersDaoStateHashes(List<DaoStateHash> stateHashes, Optional<NodeAddress> peersNodeAddress) {
        boolean useDaoMonitor = preferences.isUseFullModeDaoMonitor();
        stateHashes.forEach(peersHash -> {
            Optional<DaoStateBlock> optionalDaoStateBlock;
            // If we do not add own hashes during initial parsing we fill the missing hashes from the peer and create
            // at the last block our own hash.
            int height = peersHash.getHeight();
            if (!useDaoMonitor &&
                    !findDaoStateBlock(height).isPresent()) {
                if (daoStateService.getChainHeight() == height) {
                    // At the most recent block we create our own hash
                    optionalDaoStateBlock = daoStateService.getLastBlock()
                            .map(this::createDaoStateBlock)
                            .orElse(findDaoStateBlock(height));
                } else {
                    // Otherwise we create a block from the peers daoStateHash
                    DaoStateHash daoStateHash = new DaoStateHash(height, peersHash.getHash(), false);
                    DaoStateBlock daoStateBlock = new DaoStateBlock(daoStateHash);
                    daoStateBlockChain.add(daoStateBlock);
                    daoStateBlockByHeight.put(height, daoStateBlock);
                    daoStateHashChain.add(daoStateHash);
                    optionalDaoStateBlock = Optional.of(daoStateBlock);
                }
            } else {
                optionalDaoStateBlock = findDaoStateBlock(height);
            }

            // In any case we add the peer to our peersMap and check for conflicts on the relevant daoStateBlock
            putInPeersMapAndCheckForConflicts(optionalDaoStateBlock, getPeersAddress(peersNodeAddress), peersHash);
        });
    }

    private void putInPeersMapAndCheckForConflicts(String peersAddress, DaoStateHash peersHash) {
        putInPeersMapAndCheckForConflicts(findDaoStateBlock(peersHash.getHeight()), peersAddress, peersHash);
    }

    private void putInPeersMapAndCheckForConflicts(Optional<DaoStateBlock> optionalDaoStateBlock,
                                                   String peersAddress,
                                                   DaoStateHash peersHash) {
        optionalDaoStateBlock.ifPresent(daoStateBlock -> {
            daoStateBlock.putInPeersMap(peersAddress, peersHash);
            checkForHashConflicts(peersHash, peersAddress, daoStateBlock);
        });
    }

    private void checkForHashConflicts(DaoStateHash peersDaoStateHash,
                                       String peersNodeAddress,
                                       DaoStateBlock daoStateBlock) {
        if (daoStateBlock.getMyStateHash().hasEqualHash(peersDaoStateHash)) {
            return;
        }

        daoStateBlock.putInConflictMap(peersNodeAddress, peersDaoStateHash);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("We received a block hash from peer ")
                .append(peersNodeAddress)
                .append(" which conflicts with our block hash.\n")
                .append("my peersDaoStateHash=")
                .append(daoStateBlock.getMyStateHash())
                .append("\npeers peersDaoStateHash=")
                .append(peersDaoStateHash);
        String conflictMsg = stringBuilder.toString();

        if (isSeedNode(peersNodeAddress)) {
            isInConflictWithSeedNode = true;
            log.warn("Conflict with seed nodes: {}", conflictMsg);
        } else {
            isInConflictWithNonSeedNode = true;
            log.debug("Conflict with non-seed nodes: {}", conflictMsg);
        }
    }

    private void checkUtxos(Block block) {
        long genesisTotalSupply = daoStateService.getGenesisTotalSupply().value;
        long compensationIssuance = daoStateService.getTotalIssuedAmount(IssuanceType.COMPENSATION);
        long reimbursementIssuance = daoStateService.getTotalIssuedAmount(IssuanceType.REIMBURSEMENT);
        long totalAmountOfBurntBsq = daoStateService.getTotalAmountOfBurntBsq();
        // confiscated funds are still in the utxo set
        long sumUtxo = daoStateService.getUnspentTxOutputMap().values().stream().mapToLong(BaseTxOutput::getValue).sum();
        long sumBsq = genesisTotalSupply + compensationIssuance + reimbursementIssuance - totalAmountOfBurntBsq;

        if (sumBsq != sumUtxo) {
            utxoMismatches.add(new UtxoMismatch(block.getHeight(), sumUtxo, sumBsq));
        }
    }

    private void verifyCheckpoints() {
        // Checkpoint
        checkpoints.forEach(checkpoint -> daoStateHashChain.stream()
                .filter(daoStateHash -> daoStateHash.getHeight() == checkpoint.getHeight())
                .findAny()
                .ifPresent(daoStateHash -> {
                    if (Arrays.equals(daoStateHash.getHash(), checkpoint.getHash())) {
                        log.info("Passed checkpoint {}", checkpoint.toString());
                    } else {
                        if (checkpointFailed) {
                            return;
                        }
                        checkpointFailed = true;
                        try {
                            // Delete state and stop
                            removeFile("DaoStateStore");
                            removeFile("BlindVoteStore");
                            removeFile("ProposalStore");
                            removeFile("TempProposalStore");

                            listeners.forEach(Listener::onCheckpointFail);
                            log.error("Failed checkpoint {}", checkpoint.toString());
                        } catch (Throwable t) {
                            t.printStackTrace();
                            log.error(t.toString());
                        }
                    }
                }));
    }

    private void removeFile(String storeName) {
        long currentTime = System.currentTimeMillis();
        String newFileName = storeName + "_" + currentTime;
        String backupDirName = "out_of_sync_dao_data";
        File corrupted = new File(storageDir, storeName);
        try {
            if (corrupted.exists()) {
                FileUtil.removeAndBackupFile(storageDir, corrupted, newFileName, backupDirName);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.toString());
        }
    }

    private boolean isSeedNode(String peersNodeAddress) {
        return seedNodeAddresses.contains(peersNodeAddress);
    }

    private String getPeersAddress(Optional<NodeAddress> peersNodeAddress) {
        return peersNodeAddress.map(NodeAddress::getFullAddress)
                .orElseGet(() -> "Unknown peer " + new Random().nextInt(10000));
    }

    private Optional<DaoStateBlock> findDaoStateBlock(int height) {
        return Optional.ofNullable(daoStateBlockByHeight.get(height));
    }
}
