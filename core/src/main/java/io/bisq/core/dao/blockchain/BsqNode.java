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

package io.bisq.core.dao.blockchain;

import com.google.inject.Inject;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.proto.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.blockchain.parse.BsqParser;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.network.p2p.BootstrapListener;
import io.bisq.network.p2p.P2PService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// We are in UserThread context. We get callbacks from threaded classes which are already mapped to the UserThread.
@Slf4j
public abstract class BsqNode {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    //mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779 
    // 411812 has 693 recursions
    // MAIN NET
    private static final String GENESIS_TX_ID = "b26371e2145f52c94b3d30713a9e38305bfc665fc27cd554e794b5e369d99ef5";
    private static final int GENESIS_BLOCK_HEIGHT = 461718; // 2017-04-13
    // block 300000 2014-05-10
    // block 350000 2015-03-30
    // block 400000 2016-02-25
    // block 450000 2017-01-25

    // REG TEST
    private static final String REG_TEST_GENESIS_TX_ID = "3bc7bc9484e112ec8ddd1a1c984379819245ac463b9ce40fa8b5bf771c0f9236";
    private static final int REG_TEST_GENESIS_BLOCK_HEIGHT = 102;
    // TEST NET
    private static final String TEST_NET_GENESIS_TX_ID = "";
    private static final int TEST_NET_GENESIS_BLOCK_HEIGHT = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    protected final P2PService p2PService;
    @SuppressWarnings("WeakerAccess")
    protected final BsqParser bsqParser;
    @SuppressWarnings("WeakerAccess")
    protected final BsqChainState bsqChainState;
    @SuppressWarnings("WeakerAccess")
    protected final List<BsqChainStateListener> bsqChainStateListeners = new ArrayList<>();
    protected final String genesisTxId;
    protected final int genesisBlockHeight;

    @Getter
    protected boolean parseBlockchainComplete;
    @SuppressWarnings("WeakerAccess")
    protected boolean p2pNetworkReady;

    transient private Storage<BsqChainState> snapshotBsqChainStateStorage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqNode(BisqEnvironment bisqEnvironment,
                   P2PService p2PService,
                   BsqParser bsqParser,
                   BsqChainState bsqChainState,
                   FeeService feeService,
                   PersistenceProtoResolver persistenceProtoResolver,
                   @Named(Storage.STORAGE_DIR) File storageDir) {

        this.p2PService = p2PService;
        this.bsqParser = bsqParser;
        this.bsqChainState = bsqChainState;

        snapshotBsqChainStateStorage = new Storage<>(storageDir, persistenceProtoResolver);

        if (bisqEnvironment.getBitcoinNetwork() == BitcoinNetwork.MAINNET) {
            genesisTxId = GENESIS_TX_ID;
            genesisBlockHeight = GENESIS_BLOCK_HEIGHT;
        } else if (bisqEnvironment.getBitcoinNetwork() == BitcoinNetwork.REGTEST) {
            genesisTxId = REG_TEST_GENESIS_TX_ID;
            genesisBlockHeight = REG_TEST_GENESIS_BLOCK_HEIGHT;
        } else {
            genesisTxId = TEST_NET_GENESIS_TX_ID;
            genesisBlockHeight = TEST_NET_GENESIS_BLOCK_HEIGHT;
        }

        bsqChainState.init(snapshotBsqChainStateStorage,
                genesisTxId,
                genesisBlockHeight);

        bsqChainState.setCreateCompensationRequestFee(feeService.getCreateCompensationRequestFee().value,
                genesisBlockHeight);
        bsqChainState.setVotingFee(feeService.getVotingTxFee().value,
                genesisBlockHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        applySnapshot();

        if (p2PService.isBootstrapped()) {
            onP2PNetworkReady();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onBootstrapComplete() {
                    onP2PNetworkReady();
                }
            });
        }
    }

    private void applySnapshot() {
        BsqChainState persistedBsqChainState = snapshotBsqChainStateStorage.initAndGetPersistedWithFileName("BsqChainState");
        bsqChainState.applySnapshot(persistedBsqChainState);
        bsqChainStateListeners.stream().forEach(BsqChainStateListener::onBsqChainStateChanged);
    }

    @SuppressWarnings("WeakerAccess")
    protected void onP2PNetworkReady() {
        p2pNetworkReady = true;
    }

    @SuppressWarnings("WeakerAccess")
    protected void startParseBlocks() {
        int startBlockHeight = Math.max(genesisBlockHeight, bsqChainState.getChainHeadHeight() + 1);
        log.info("Parse blocks:\n" +
                        "   Start block height={}\n" +
                        "   Genesis txId={}\n" +
                        "   Genesis block height={}\n" +
                        "   BsqChainState block height={}\n",
                startBlockHeight,
                genesisTxId,
                genesisBlockHeight,
                bsqChainState.getChainHeadHeight());

        parseBlocksWithChainHeadHeight(startBlockHeight,
                genesisBlockHeight,
                genesisTxId);
    }

    abstract protected void parseBlocksWithChainHeadHeight(int startBlockHeight, int genesisBlockHeight, String genesisTxId);

    abstract protected void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId, Integer chainHeadHeight);

    abstract protected void onParseBlockchainComplete(int genesisBlockHeight, String genesisTxId);

    @SuppressWarnings("WeakerAccess")
    protected void onNewBsqBlock(BsqBlock bsqBlock) {
        bsqChainStateListeners.stream().forEach(BsqChainStateListener::onBsqChainStateChanged);
    }

    //TODO
    @SuppressWarnings("WeakerAccess")
    protected void startReOrgFromLastSnapshot() {
        applySnapshot();
        startParseBlocks();
    }

    public void addBsqChainStateListener(BsqChainStateListener bsqChainStateListener) {
        bsqChainStateListeners.add(bsqChainStateListener);
    }

    public void removeBsqChainStateListener(BsqChainStateListener bsqChainStateListener) {
        bsqChainStateListeners.remove(bsqChainStateListener);
    }
}
