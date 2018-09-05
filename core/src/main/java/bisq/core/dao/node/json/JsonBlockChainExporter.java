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

package bisq.core.dao.node.json;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.state.BsqState;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.PubKeyScript;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxType;

import bisq.common.storage.FileUtil;
import bisq.common.storage.JsonFileManager;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public class JsonBlockChainExporter {
    private final BsqStateService bsqStateService;
    private final boolean dumpBlockchainData;

    private final ListeningExecutorService executor = Utilities.getListeningExecutorService("JsonExporter", 1, 1, 1200);
    private JsonFileManager txFileManager, txOutputFileManager, jsonFileManager;

    @Inject
    public JsonBlockChainExporter(BsqStateService bsqStateService,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.bsqStateService = bsqStateService;
        this.dumpBlockchainData = dumpBlockchainData;

        init(storageDir, dumpBlockchainData);
    }

    private void init(@Named(Storage.STORAGE_DIR) File storageDir, @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        if (dumpBlockchainData) {
            File txDir = new File(Paths.get(storageDir.getAbsolutePath(), "tx").toString());
            File txOutputDir = new File(Paths.get(storageDir.getAbsolutePath(), "txo").toString());
            File blockchainDir = new File(Paths.get(storageDir.getAbsolutePath(), "all").toString());
            try {
                if (txDir.exists())
                    FileUtil.deleteDirectory(txDir);
                if (txOutputDir.exists())
                    FileUtil.deleteDirectory(txOutputDir);
                if (blockchainDir.exists())
                    FileUtil.deleteDirectory(blockchainDir);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (!txDir.mkdir())
                log.warn("make txDir failed.\ntxDir=" + txDir.getAbsolutePath());

            if (!txOutputDir.mkdir())
                log.warn("make txOutputDir failed.\ntxOutputDir=" + txOutputDir.getAbsolutePath());

            if (!blockchainDir.mkdir())
                log.warn("make blockchainDir failed.\nblockchainDir=" + blockchainDir.getAbsolutePath());

            txFileManager = new JsonFileManager(txDir);
            txOutputFileManager = new JsonFileManager(txOutputDir);
            jsonFileManager = new JsonFileManager(blockchainDir);
        }
    }

    public void shutDown() {
        if (dumpBlockchainData) {
            txFileManager.shutDown();
            txOutputFileManager.shutDown();
            jsonFileManager.shutDown();
        }
    }

    public void maybeExport() {
        if (dumpBlockchainData) {
            ListenableFuture<Void> future = executor.submit(() -> {
                final BsqState bsqStateClone = bsqStateService.getClone();
                Map<String, Tx> txMap = bsqStateService.getBlocksFromState(bsqStateClone).stream()
                        .filter(Objects::nonNull)
                        .flatMap(block -> block.getTxs().stream())
                        .collect(Collectors.toMap(Tx::getId, tx -> tx));
                for (Tx tx : txMap.values()) {
                    String txId = tx.getId();
                    final Optional<TxType> optionalTxType = bsqStateService.getOptionalTxType(txId);
                    optionalTxType.ifPresent(txType1 -> {
                        JsonTxType txType = txType1 != TxType.UNDEFINED_TX_TYPE ?
                                JsonTxType.valueOf(txType1.name()) : null;
                        List<JsonTxOutput> outputs = new ArrayList<>();
                        tx.getTxOutputs().forEach(txOutput -> {
                            final Optional<SpentInfo> optionalSpentInfo = bsqStateService.getSpentInfo(txOutput);
                            final boolean isBsqOutput = bsqStateService.isBsqTxOutputType(txOutput);
                            final PubKeyScript pubKeyScript = txOutput.getPubKeyScript();
                            final JsonTxOutput outputForJson = new JsonTxOutput(txId,
                                    txOutput.getIndex(),
                                    isBsqOutput ? txOutput.getValue() : 0,
                                    !isBsqOutput ? txOutput.getValue() : 0,
                                    txOutput.getBlockHeight(),
                                    isBsqOutput,
                                    bsqStateService.getBurntFee(tx.getId()),
                                    txOutput.getAddress(),
                                    pubKeyScript != null ? new JsonScriptPubKey(pubKeyScript) : null,
                                    optionalSpentInfo.map(JsonSpentInfo::new).orElse(null),
                                    tx.getTime(),
                                    txType,
                                    txType != null ? txType.getDisplayString() : "",
                                    txOutput.getOpReturnData() != null ? Utils.HEX.encode(txOutput.getOpReturnData()) : null
                            );
                            outputs.add(outputForJson);
                            txOutputFileManager.writeToDisc(Utilities.objectToJson(outputForJson), outputForJson.getId());
                        });


                        List<JsonTxInput> inputs = tx.getTxInputs().stream()
                                .map(txInput -> {
                                    Optional<TxOutput> optionalTxOutput = bsqStateService.getConnectedTxOutput(txInput);
                                    if (optionalTxOutput.isPresent()) {
                                        final TxOutput connectedTxOutput = optionalTxOutput.get();
                                        final boolean isBsqOutput = bsqStateService.isBsqTxOutputType(connectedTxOutput);
                                        return new JsonTxInput(txInput.getConnectedTxOutputIndex(),
                                                txInput.getConnectedTxOutputTxId(),
                                                connectedTxOutput.getValue(),
                                                isBsqOutput,
                                                connectedTxOutput.getAddress(),
                                                tx.getTime());
                                    } else {
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        final JsonTx jsonTx = new JsonTx(txId,
                                tx.getBlockHeight(),
                                tx.getBlockHash(),
                                tx.getTime(),
                                inputs,
                                outputs,
                                txType,
                                txType != null ? txType.getDisplayString() : "",
                                bsqStateService.getBurntFee(tx.getId()));

                        txFileManager.writeToDisc(Utilities.objectToJson(jsonTx), txId);
                    });
                }

                jsonFileManager.writeToDisc(Utilities.objectToJson(bsqStateClone), "BsqStateService");
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
