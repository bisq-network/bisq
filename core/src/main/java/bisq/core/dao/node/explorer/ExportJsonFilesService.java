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

package bisq.core.dao.node.explorer;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import com.google.inject.Inject;

import javax.inject.Named;

import java.nio.file.Paths;

import java.io.File;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExportJsonFilesService implements DaoSetupService {
    private final DaoStateService daoStateService;
    private final File storageDir;
    private boolean dumpBlockchainData;
    private JsonFileManager blockFileManager, txFileManager, txOutputFileManager, bsqStateFileManager;
    private File blockDir;

    @Inject
    public ExportJsonFilesService(DaoStateService daoStateService,
                                  @Named(Config.STORAGE_DIR) File storageDir,
                                  @Named(Config.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.daoStateService = daoStateService;
        this.storageDir = storageDir;
        this.dumpBlockchainData = dumpBlockchainData;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
        if (!dumpBlockchainData) {
            return;
        }

        File jsonDir = new File(Paths.get(storageDir.getAbsolutePath(), "json").toString());
        blockDir = new File(Paths.get(storageDir.getAbsolutePath(), "json", "block").toString());
        File txDir = new File(Paths.get(storageDir.getAbsolutePath(), "json", "tx").toString());
        File txOutputDir = new File(Paths.get(storageDir.getAbsolutePath(), "json", "txo").toString());
        File bsqStateDir = new File(Paths.get(storageDir.getAbsolutePath(), "json", "all").toString());

        if (!jsonDir.mkdir())
            log.warn("make jsonDir failed.\njsonDir=" + jsonDir.getAbsolutePath());

        if (!blockDir.mkdir())
            log.warn("make blockDir failed.\njsonDir=" + blockDir.getAbsolutePath());

        if (!txDir.mkdir())
            log.warn("make txDir failed.\ntxDir=" + txDir.getAbsolutePath());

        if (!txOutputDir.mkdir())
            log.warn("make txOutputDir failed.\ntxOutputDir=" + txOutputDir.getAbsolutePath());

        if (!bsqStateDir.mkdir())
            log.warn("make bsqStateDir failed.\nbsqStateDir=" + bsqStateDir.getAbsolutePath());

        blockFileManager = new JsonFileManager(blockDir);
        txFileManager = new JsonFileManager(txDir);
        txOutputFileManager = new JsonFileManager(txOutputDir);
        bsqStateFileManager = new JsonFileManager(bsqStateDir);
    }

    public void shutDown() {
        if (!dumpBlockchainData) {
            return;
        }

        blockFileManager.shutDown();
        txFileManager.shutDown();
        txOutputFileManager.shutDown();
        bsqStateFileManager.shutDown();
        dumpBlockchainData = false;
    }

    public void onNewBlock(Block block) {
        if (!dumpBlockchainData) {
            return;
        }

        // We do write the block on the main thread as the overhead to create a thread and risk for inconsistency is not
        // worth the potential performance gain.
        processBlock(block, true);
    }

    private void processBlock(Block block, boolean doDumpDaoState) {
        int lastPersistedBlock = getLastPersistedBlock();
        if (block.getHeight() <= lastPersistedBlock) {
            return;
        }

        long ts = System.currentTimeMillis();
        JsonBlock jsonBlock = getJsonBlock(block);
        blockFileManager.writeToDisc(Utilities.objectToJson(jsonBlock), String.valueOf(jsonBlock.getHeight()));

        jsonBlock.getTxs().forEach(jsonTx -> {
            txFileManager.writeToDisc(Utilities.objectToJson(jsonTx), jsonTx.getId());

            jsonTx.getOutputs().forEach(jsonTxOutput ->
                    txOutputFileManager.writeToDisc(Utilities.objectToJson(jsonTxOutput), jsonTxOutput.getId()));
        });

        log.info("Write json data for block {} took {} ms", block.getHeight(), System.currentTimeMillis() - ts);

        if (doDumpDaoState) {
            dumpDaoState();
        }
    }

    public void onParseBlockChainComplete() {
        if (!dumpBlockchainData) {
            return;
        }

        int lastPersistedBlock = getLastPersistedBlock();
        List<Block> blocks = daoStateService.getBlocksFromBlockHeight(lastPersistedBlock + 1, Integer.MAX_VALUE);

        // We use a thread here to write all past blocks to avoid that the main thread gets blocked for too long.
        new Thread(() -> {
            Thread.currentThread().setName("Write all blocks to json");
            blocks.forEach(e -> processBlock(e, false));
        }).start();

        dumpDaoState();
    }

    private void dumpDaoState() {
        // TODO we should get rid of that data structure and use the individual jsonBlocks instead as we cannot cache data
        // here and re-write each time the full blockchain which is already > 200 MB
        // Once the webapp has impl the changes we can delete that here.
        long ts = System.currentTimeMillis();
        List<JsonBlock> jsonBlockList = daoStateService.getBlocks().stream()
                .map(this::getJsonBlock)
                .collect(Collectors.toList());
        JsonBlocks jsonBlocks = new JsonBlocks(daoStateService.getChainHeight(), jsonBlockList);

        // We use here the thread write method as the data is quite large and write can take a bit
        bsqStateFileManager.writeToDiscThreaded(Utilities.objectToJson(jsonBlocks), "blocks");
        log.info("Dumping full bsqState with {} blocks took {} ms",
                jsonBlocks.getBlocks().size(), System.currentTimeMillis() - ts);
    }

    private int getLastPersistedBlock() {
        // At start we use one block before genesis
        int result = daoStateService.getGenesisBlockHeight() - 1;
        String[] list = blockDir.list();
        if (list != null && list.length > 0) {
            List<Integer> blocks = Arrays.stream(list)
                    .filter(e -> !e.endsWith(".tmp"))
                    .map(e -> e.replace(".json", ""))
                    .map(Integer::valueOf)
                    .sorted()
                    .collect(Collectors.toList());
            if (!blocks.isEmpty()) {
                Integer lastBlockHeight = blocks.get(blocks.size() - 1);
                if (lastBlockHeight > result) {
                    result = lastBlockHeight;
                }
            }
        }
        return result;
    }

    private JsonBlock getJsonBlock(Block block) {
        List<JsonTx> jsonTxs = block.getTxs().stream()
                .map(this::getJsonTx)
                .collect(Collectors.toList());
        return new JsonBlock(block.getHeight(),
                block.getTime(),
                block.getHash(),
                block.getPreviousBlockHash(),
                jsonTxs);
    }

    private JsonTx getJsonTx(Tx tx) {
        JsonTxType jsonTxType = getJsonTxType(tx);
        String jsonTxTypeDisplayString = getJsonTxTypeDisplayString(jsonTxType);
        return new JsonTx(tx.getId(),
                tx.getBlockHeight(),
                tx.getBlockHash(),
                tx.getTime(),
                getJsonTxInputs(tx),
                getJsonTxOutputs(tx),
                jsonTxType,
                jsonTxTypeDisplayString,
                tx.getBurntFee(),
                tx.getInvalidatedBsq(),
                tx.getUnlockBlockHeight());
    }

    private List<JsonTxInput> getJsonTxInputs(Tx tx) {
        return tx.getTxInputs().stream()
                .map(txInput -> {
                    Optional<TxOutput> optionalTxOutput = daoStateService.getConnectedTxOutput(txInput);
                    if (optionalTxOutput.isPresent()) {
                        TxOutput connectedTxOutput = optionalTxOutput.get();
                        boolean isBsqTxOutputType = daoStateService.isBsqTxOutputType(connectedTxOutput);
                        return new JsonTxInput(txInput.getConnectedTxOutputIndex(),
                                txInput.getConnectedTxOutputTxId(),
                                connectedTxOutput.getValue(),
                                isBsqTxOutputType,
                                connectedTxOutput.getAddress(),
                                tx.getTime());
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<JsonTxOutput> getJsonTxOutputs(Tx tx) {
        JsonTxType jsonTxType = getJsonTxType(tx);
        String jsonTxTypeDisplayString = getJsonTxTypeDisplayString(jsonTxType);
        return tx.getTxOutputs().stream()
                .map(txOutput -> {
                    boolean isBsqTxOutputType = daoStateService.isBsqTxOutputType(txOutput);
                    long bsqAmount = isBsqTxOutputType ? txOutput.getValue() : 0;
                    long btcAmount = !isBsqTxOutputType ? txOutput.getValue() : 0;
                    PubKeyScript pubKeyScript = txOutput.getPubKeyScript();
                    JsonScriptPubKey scriptPubKey = pubKeyScript != null ? new JsonScriptPubKey(pubKeyScript) : null;
                    JsonSpentInfo spentInfo = daoStateService.getSpentInfo(txOutput).map(JsonSpentInfo::new).orElse(null);
                    JsonTxOutputType txOutputType = JsonTxOutputType.valueOf(txOutput.getTxOutputType().name());
                    int lockTime = txOutput.getLockTime();
                    String opReturn = txOutput.getOpReturnData() != null ? Utils.HEX.encode(txOutput.getOpReturnData()) : null;
                    boolean isUnspent = daoStateService.isUnspent(txOutput.getKey());
                    return new JsonTxOutput(tx.getId(),
                            txOutput.getIndex(),
                            bsqAmount,
                            btcAmount,
                            tx.getBlockHeight(),
                            isBsqTxOutputType,
                            tx.getBurntFee(),
                            tx.getInvalidatedBsq(),
                            txOutput.getAddress(),
                            scriptPubKey,
                            spentInfo,
                            tx.getTime(),
                            jsonTxType,
                            jsonTxTypeDisplayString,
                            txOutputType,
                            txOutputType.getDisplayString(),
                            opReturn,
                            lockTime,
                            isUnspent
                    );
                })
                .collect(Collectors.toList());
    }

    private String getJsonTxTypeDisplayString(JsonTxType jsonTxType) {
        return jsonTxType != null ? jsonTxType.getDisplayString() : "";
    }

    private JsonTxType getJsonTxType(Tx tx) {
        TxType txType = tx.getTxType();
        return txType != null ? JsonTxType.valueOf(txType.name()) : null;
    }
}
