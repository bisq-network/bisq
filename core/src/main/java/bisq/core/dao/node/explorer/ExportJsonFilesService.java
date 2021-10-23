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
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.util.JsonUtil;

import bisq.common.config.Config;
import bisq.common.file.FileUtil;
import bisq.common.file.JsonFileManager;
import bisq.common.util.GcUtil;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExportJsonFilesService implements DaoSetupService {
    private final DaoStateService daoStateService;
    private final File storageDir;
    private final boolean dumpBlockchainData;

    private final ListeningExecutorService executor = Utilities.getListeningExecutorService("JsonExporter",
            1, 1, 1200);
    private JsonFileManager txFileManager, txOutputFileManager, bsqStateFileManager;

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
        if (dumpBlockchainData) {
            File jsonDir = new File(Paths.get(storageDir.getAbsolutePath(), "json").toString());
            File txDir = new File(Paths.get(storageDir.getAbsolutePath(), "json", "tx").toString());
            File txOutputDir = new File(Paths.get(storageDir.getAbsolutePath(), "json", "txo").toString());
            File bsqStateDir = new File(Paths.get(storageDir.getAbsolutePath(), "json", "all").toString());
            try {
                if (txDir.exists())
                    FileUtil.deleteDirectory(txDir);
                if (txOutputDir.exists())
                    FileUtil.deleteDirectory(txOutputDir);
                if (bsqStateDir.exists())
                    FileUtil.deleteDirectory(bsqStateDir);
                if (jsonDir.exists())
                    FileUtil.deleteDirectory(jsonDir);
            } catch (IOException e) {
                log.error(e.toString());
                e.printStackTrace();
            }

            if (!jsonDir.mkdir())
                log.warn("make jsonDir failed.\njsonDir=" + jsonDir.getAbsolutePath());

            if (!txDir.mkdir())
                log.warn("make txDir failed.\ntxDir=" + txDir.getAbsolutePath());

            if (!txOutputDir.mkdir())
                log.warn("make txOutputDir failed.\ntxOutputDir=" + txOutputDir.getAbsolutePath());

            if (!bsqStateDir.mkdir())
                log.warn("make bsqStateDir failed.\nbsqStateDir=" + bsqStateDir.getAbsolutePath());

            txFileManager = new JsonFileManager(txDir);
            txOutputFileManager = new JsonFileManager(txOutputDir);
            bsqStateFileManager = new JsonFileManager(bsqStateDir);
        }
    }

    public void shutDown() {
        if (dumpBlockchainData && txFileManager != null) {
            txFileManager.shutDown();
            txOutputFileManager.shutDown();
            bsqStateFileManager.shutDown();
        }
    }

    public void maybeExportToJson() {
        if (dumpBlockchainData &&
                daoStateService.isParseBlockChainComplete()) {
            // We store the data we need once we write the data to disk (in the thread) locally.
            // Access to daoStateService is single threaded, we must not access daoStateService from the thread.
            List<JsonTxOutput> allJsonTxOutputs = new ArrayList<>();

            List<JsonTx> jsonTxs = daoStateService.getUnorderedTxStream()
                    .map(tx -> {
                        JsonTx jsonTx = getJsonTx(tx);
                        allJsonTxOutputs.addAll(jsonTx.getOutputs());
                        return jsonTx;
                    }).collect(Collectors.toList());

            GcUtil.maybeReleaseMemory();

            DaoState daoState = daoStateService.getClone();
            List<JsonBlock> jsonBlockList = daoState.getBlocks().stream()
                    .map(this::getJsonBlock)
                    .collect(Collectors.toList());
            JsonBlocks jsonBlocks = new JsonBlocks(daoState.getChainHeight(), jsonBlockList);

            ListenableFuture<Void> future = executor.submit(() -> {
                bsqStateFileManager.writeToDisc(JsonUtil.objectToJson(jsonBlocks), "blocks");
                allJsonTxOutputs.forEach(jsonTxOutput -> txOutputFileManager.writeToDisc(JsonUtil.objectToJson(jsonTxOutput), jsonTxOutput.getId()));
                jsonTxs.forEach(jsonTx -> txFileManager.writeToDisc(JsonUtil.objectToJson(jsonTx), jsonTx.getId()));

                GcUtil.maybeReleaseMemory();

                return null;
            });

            Futures.addCallback(future, Utilities.failureCallback(throwable -> {
                log.error(throwable.toString());
                throwable.printStackTrace();
            }), MoreExecutors.directExecutor());
        }
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
