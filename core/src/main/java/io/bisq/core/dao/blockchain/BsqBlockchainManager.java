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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.neemre.btcdcli4j.core.domain.PubKeyScript;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.storage.PlainTextWrapper;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.dao.RpcOptionKeys;
import io.bisq.core.dao.blockchain.json.ScriptPubKeyForJson;
import io.bisq.core.dao.blockchain.json.SpentInfoForJson;
import io.bisq.core.dao.blockchain.json.TxOutputForJson;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BsqBlockchainManager {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainManager.class);

    //mainnet
    private static final String GENESIS_TX_ID = "cabbf6073aea8f22ec678e973ac30c6d8fc89321011da6a017f63e67b9f66667";
    // block 300000 2014-05-10 
    // block 350000 2015-03-30
    // block 400000 2016-02-25
    // block 450000 2017-01-25
    private static final int GENESIS_BLOCK_HEIGHT = 400000;
    private static final String REG_TEST_GENESIS_TX_ID = "3bc7bc9484e112ec8ddd1a1c984379819245ac463b9ce40fa8b5bf771c0f9236";
    private static final int REG_TEST_GENESIS_BLOCK_HEIGHT = 102;

    // Modulo of blocks for making snapshots of UTXO. 
    // We stay also the value behind for safety against reorgs.
    // E.g. for SNAPSHOT_TRIGGER = 30:
    // If we are block 119 and last snapshot was 60 then we get a new trigger for a snapshot at block 120 and
    // new snapshot is block 90. We only persist at the new snapshot, so we always re-parse from latest snapshot after 
    // a restart.
    private static final int SNAPSHOT_TRIGGER = 300000;
    private final boolean connectToBtcCore;

    public static int getSnapshotTrigger() {
        return SNAPSHOT_TRIGGER;
    }


    private final BsqBlockchainService blockchainService;
    private File storageDir;
    private final boolean dumpBlockchainData;
    private final BitcoinNetwork bitcoinNetwork;
    private final List<BsqUTXOListener> bsqUTXOListeners = new ArrayList<>();
    private final List<BsqTxoListener> bsqTxoListeners = new ArrayList<>();

    @Getter
    private final BsqUTXOMap bsqUTXOMap;
    @Getter
    private final BsqTXOMap bsqTXOMap;
    @Getter
    private int chainHeadHeight;
    @Getter
    private boolean isUtxoSyncWithChainHeadHeight;
    private final Storage<PlainTextWrapper> jsonStorage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainManager(BsqBlockchainService blockchainService,
                                BisqEnvironment bisqEnvironment,
                                @Named(Storage.DIR_KEY) File storageDir,
                                Storage<PlainTextWrapper> jsonStorage,
                                @Named(RpcOptionKeys.RPC_USER) String rpcUser,
                                @Named(RpcOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.blockchainService = blockchainService;
        this.storageDir = storageDir;
        this.jsonStorage = jsonStorage;
        this.dumpBlockchainData = dumpBlockchainData;
        this.bitcoinNetwork = bisqEnvironment.getBitcoinNetwork();

        connectToBtcCore = rpcUser != null && !rpcUser.isEmpty();
        bsqUTXOMap = new BsqUTXOMap(storageDir);
        bsqTXOMap = new BsqTXOMap(storageDir);

        bsqUTXOMap.addListener(c -> onBsqUTXOChanged());
        bsqTXOMap.addListener(c -> onBsqTXOChanged());
        bsqTXOMap.addBurnedBSQTxMapListener(c -> onBsqTXOChanged());

        if (dumpBlockchainData) {
            this.jsonStorage.initWithFileName("txo.json");
          /*  p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onBootstrapComplete() {
                    addOfferBookChangedListener(new OfferBookChangedListener() {
                        @Override
                        public void onAdded(Offer offer) {
                            doDumpBlockchainData();
                        }

                        @Override
                        public void onRemoved(Offer offer) {
                            doDumpBlockchainData();
                        }
                    });
                    UserThread.runAfter(BsqBlockchainManager.this::doDumpBlockchainData, 1);
                }
            });*/
        }
    }

    private void doDumpBlockchainData() {
        List<TxOutputForJson> list = bsqTXOMap.getMap().values().stream()
                .map(this::getTxOutputForJson)
                .collect(Collectors.toList());

        list.sort((o1, o2) -> (o1.getSortData().compareTo(o2.getSortData())));
        TxOutputForJson[] array = new TxOutputForJson[list.size()];
        list.toArray(array);
        jsonStorage.queueUpForSave(new PlainTextWrapper(Utilities.objectToJson(array)), 5000);

        // keep the individual file storage option as code as we dont know yet what we will use.
      /*  log.error("txOutputForJson " + txOutputForJson);
        File txoDir = new File(Paths.get(storageDir.getAbsolutePath(), "txo").toString());
        if (!txoDir.exists())
            if (!txoDir.mkdir())
                log.warn("make txoDir failed.\ntxoDir=" + txoDir.getAbsolutePath());
        File txoFile = new File(Paths.get(txoDir.getAbsolutePath(),
                txOutput.getTxId() + ":" + outputIndex + ".json").toString());

        // Nr of write requests might be a bit heavy, consider write whole list to one file
        FileManager<PlainTextWrapper> fileManager = new FileManager<>(storageDir, txoFile, 1);
        fileManager.saveLater(new PlainTextWrapper(Utilities.objectToJson(txOutputForJson)));*/
    }

    private TxOutputForJson getTxOutputForJson(TxOutput txOutput) {
        String txId = txOutput.getTxId();
        int outputIndex = txOutput.getIndex();
        final long bsqAmount = txOutput.getValue();
        final int height = txOutput.getBlockHeight();
        final boolean isBsqCoinBase = txOutput.isBsqCoinBase();
        final boolean verified = txOutput.isVerified();
        final long burnedFee = txOutput.getBurnedFee();
        final long btcTxFee = txOutput.getBtcTxFee();

        PubKeyScript pubKeyScript = txOutput.getPubKeyScript();
        final ScriptPubKeyForJson scriptPubKey = new ScriptPubKeyForJson(pubKeyScript.getAddresses(),
                pubKeyScript.getAsm(),
                pubKeyScript.getHex(),
                pubKeyScript.getReqSigs(),
                pubKeyScript.getType().toString());
        SpentInfoForJson spentInfoJson = null;
        SpendInfo spendInfo = txOutput.getSpendInfo();
        if (spendInfo != null)
            spentInfoJson = new SpentInfoForJson(spendInfo.getBlockHeight(),
                    spendInfo.getInputIndex(),
                    spendInfo.getTxId());

        final long time = txOutput.getTime();
        final String txVersion = txOutput.getTxVersion();
        return new TxOutputForJson(txId,
                outputIndex,
                bsqAmount,
                height,
                isBsqCoinBase,
                verified,
                burnedFee,
                btcTxFee,
                scriptPubKey,
                spentInfoJson,
                time,
                txVersion
        );
    }

    private void onBsqUTXOChanged() {
        bsqUTXOListeners.stream().forEach(e -> e.onBsqUTXOChanged(bsqUTXOMap));
    }

    private void onBsqTXOChanged() {
        bsqTxoListeners.stream().forEach(e -> e.onBsqTxoChanged(bsqTXOMap));
        if (dumpBlockchainData)
            doDumpBlockchainData();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        if (connectToBtcCore)
            blockchainService.setup(this::blockchainServiceSetupCompleted, errorMessageHandler);
    }

    public Set<String> getUtxoTxIdSet() {
        return bsqUTXOMap.getTxIdSet();
    }

    public Set<String> getTxoTxIdSet() {
        return bsqTXOMap.getTxIdSet();
    }

    public void addUtxoListener(BsqUTXOListener bsqUTXOListener) {
        bsqUTXOListeners.add(bsqUTXOListener);
    }

    public void removeUtxoListener(BsqUTXOListener bsqUTXOListener) {
        bsqUTXOListeners.remove(bsqUTXOListener);
    }

    public void addTxoListener(BsqTxoListener bsqTxoListener) {
        bsqTxoListeners.add(bsqTxoListener);
    }

    public void removeTxoListener(BsqTxoListener bsqTxoListener) {
        bsqTxoListeners.remove(bsqTxoListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void blockchainServiceSetupCompleted() {
        final int genesisBlockHeight = getGenesisBlockHeight();
        final String genesisTxId = getGenesisTxId();
        int startBlockHeight = Math.max(genesisBlockHeight, bsqUTXOMap.getSnapshotHeight());
        log.info("genesisTxId=" + genesisTxId);
        log.info("genesisBlockHeight=" + genesisBlockHeight);
        log.info("startBlockHeight=" + startBlockHeight);
        log.info("bsqUTXOMap.getSnapshotHeight()=" + bsqUTXOMap.getSnapshotHeight());

        if (bsqUTXOMap.getSnapshotHeight() > 0)
            onBsqUTXOChanged();

        ListenableFuture<Integer> future =
                blockchainService.executeParseBlockchain(bsqUTXOMap,
                        bsqTXOMap,
                        startBlockHeight,
                        genesisBlockHeight,
                        genesisTxId);

        Futures.addCallback(future, new FutureCallback<Integer>() {
            @Override
            public void onSuccess(Integer height) {
                UserThread.execute(() -> {
                    chainHeadHeight = height;
                    isUtxoSyncWithChainHeadHeight = true;
                    blockchainService.parseBlockchainCompete(btcdBlock -> {
                        if (btcdBlock != null) {
                            UserThread.execute(() -> {
                                try {
                                    final BsqBlock bsqBlock = new BsqBlock(btcdBlock.getTx(), btcdBlock.getHeight());
                                    blockchainService.parseBlock(bsqBlock,
                                            genesisBlockHeight,
                                            genesisTxId,
                                            bsqUTXOMap,
                                            bsqTXOMap);
                                } catch (BsqBlockchainException e) {
                                    //TODO
                                    e.printStackTrace();
                                }
                            });
                        }
                    });
                });
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> log.error("syncFromGenesis failed" + throwable.toString()));
            }
        });
    }

    private String getGenesisTxId() {
        return bitcoinNetwork == BitcoinNetwork.REGTEST ? REG_TEST_GENESIS_TX_ID : GENESIS_TX_ID;
    }

    private int getGenesisBlockHeight() {
        return bitcoinNetwork == BitcoinNetwork.REGTEST ? REG_TEST_GENESIS_BLOCK_HEIGHT : GENESIS_BLOCK_HEIGHT;
    }
}
