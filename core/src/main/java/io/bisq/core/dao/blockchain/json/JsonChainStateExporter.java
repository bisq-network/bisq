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

package io.bisq.core.dao.blockchain.json;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Inject;
import io.bisq.common.storage.FileUtil;
import io.bisq.common.storage.JsonFileManager;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Utils;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class JsonChainStateExporter {
    private final boolean dumpBlockchainData;
    private final BsqChainState bsqChainState;

    private final ListeningExecutorService executor = Utilities.getListeningExecutorService("JsonExporter", 1, 1, 1200);
    private File txDir, txOutputDir, bsqChainStateDir;
    private JsonFileManager txFileManager, txOutputFileManager, bsqChainStateFileManager;

    @Inject
    public JsonChainStateExporter(BsqChainState bsqChainState,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.bsqChainState = bsqChainState;
        this.dumpBlockchainData = dumpBlockchainData;

        init(storageDir, dumpBlockchainData);
    }

    private void init(@Named(Storage.STORAGE_DIR) File storageDir, @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        if (dumpBlockchainData) {
            txDir = new File(Paths.get(storageDir.getAbsolutePath(), "tx").toString());
            txOutputDir = new File(Paths.get(storageDir.getAbsolutePath(), "txo").toString());
            bsqChainStateDir = new File(Paths.get(storageDir.getAbsolutePath(), "all").toString());
            try {
                if (txDir.exists())
                    FileUtil.deleteDirectory(txDir);
                if (txOutputDir.exists())
                    FileUtil.deleteDirectory(txOutputDir);
                if (bsqChainStateDir.exists())
                    FileUtil.deleteDirectory(bsqChainStateDir);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (!txDir.mkdir())
                log.warn("make txDir failed.\ntxDir=" + txDir.getAbsolutePath());

            if (!txOutputDir.mkdir())
                log.warn("make txOutputDir failed.\ntxOutputDir=" + txOutputDir.getAbsolutePath());

            if (!bsqChainStateDir.mkdir())
                log.warn("make bsqChainStateDir failed.\nbsqChainStateDir=" + bsqChainStateDir.getAbsolutePath());

            txFileManager = new JsonFileManager(txDir);
            txOutputFileManager = new JsonFileManager(txOutputDir);
            bsqChainStateFileManager = new JsonFileManager(bsqChainStateDir);
        }
    }

    public void shutDown() {
        if (dumpBlockchainData) {
            txFileManager.shutDown();
            txOutputFileManager.shutDown();
            bsqChainStateFileManager.shutDown();
        }
    }

    public void maybeExport() {
        if (dumpBlockchainData) {
            ListenableFuture<Void> future = executor.submit(() -> {
                final BsqChainState bsqChainStateClone = bsqChainState.getClone();
                for (Tx tx : bsqChainStateClone.getTxMap().values()) {
                    String txId = tx.getId();
                    JsonTxType txType = tx.getTxType() != null ? JsonTxType.valueOf(tx.getTxType().name()) : null;
                    List<JsonTxOutput> outputs = new ArrayList<>();
                    tx.getOutputs().stream().forEach(txOutput -> {
                        final JsonTxOutput outputForJson = new JsonTxOutput(txId,
                                txOutput.getIndex(),
                                txOutput.isVerified() ? txOutput.getValue() : 0,
                                !txOutput.isVerified() ? txOutput.getValue() : 0,
                                txOutput.getBlockHeight(),
                                txOutput.isVerified(),
                                tx.getBurntFee(),
                                txOutput.getAddress(),
                                new JsonScriptPubKey(txOutput.getPubKeyScript()),
                                txOutput.getSpentInfo() != null ?
                                        new JsonSpentInfo(txOutput.getSpentInfo()) : null,
                                tx.getTime(),
                                txType,
                                txType != null ? txType.getDisplayString() : "",
                                txOutput.getOpReturnData() != null ? Utils.HEX.encode(txOutput.getOpReturnData()) : null
                        );
                        outputs.add(outputForJson);
                        txOutputFileManager.writeToDisc(Utilities.objectToJson(outputForJson), outputForJson.getId());
                    });


                    List<JsonTxInput> inputs = tx.getInputs().stream()
                            .map(txInput -> {
                                final TxOutput connectedTxOutput = txInput.getConnectedTxOutput();
                                return new JsonTxInput(txInput.getTxOutputIndex(),
                                        txInput.getTxId(),
                                        connectedTxOutput != null ? connectedTxOutput.getValue() : 0,
                                        connectedTxOutput != null && connectedTxOutput.isVerified(),
                                        connectedTxOutput != null ? connectedTxOutput.getAddress() : null,
                                        tx.getTime());
                            })
                            .collect(Collectors.toList());

                    final JsonTx jsonTx = new JsonTx(txId,
                            tx.getBlockHeight(),
                            tx.getBlockHash(),
                            tx.getTime(),
                            inputs,
                            outputs,
                            txType,
                            txType != null ? txType.getDisplayString() : "",
                            tx.getBurntFee());

                    txFileManager.writeToDisc(Utilities.objectToJson(jsonTx), txId);
                }

                bsqChainStateFileManager.writeToDisc(Utilities.objectToJson(bsqChainStateClone), "bsqChainState");
                return null;
            });

            Futures.addCallback(future, new FutureCallback<Void>() {
                public void onSuccess(Void ignore) {
                    log.trace("onSuccess");
                }

                public void onFailure(@NotNull Throwable throwable) {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                }
            });
        }
    }
}
