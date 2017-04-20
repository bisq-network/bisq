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

package io.bisq.core.dao.blockchain.json;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Inject;
import io.bisq.common.storage.JsonFileManager;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.blockchain.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DaoJsonExporter {
    private final boolean dumpBlockchainData;

    private final ListeningExecutorService executor = Utilities.getListeningExecutorService("JsonExporter", 1, 1, 1200);
    private File txDir, txOutputDir, bsqChainStateDir;
    private JsonFileManager txFileManager, txOutputFileManager, bsqChainStateFileManager;
    private BsqChainState bsqChainState;

    @Inject
    public DaoJsonExporter(BsqChainState bsqChainState,
                           @Named(Storage.STORAGE_DIR) File storageDir,
                           @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.bsqChainState = bsqChainState;
        this.dumpBlockchainData = dumpBlockchainData;

        if (dumpBlockchainData) {
            txDir = new File(Paths.get(storageDir.getAbsolutePath(), "tx").toString());
            if (!txDir.exists())
                if (!txDir.mkdir())
                    log.warn("make txDir failed.\ntxDir=" + txDir.getAbsolutePath());

            txOutputDir = new File(Paths.get(storageDir.getAbsolutePath(), "txo").toString());
            if (!txOutputDir.exists())
                if (!txOutputDir.mkdir())
                    log.warn("make txOutputDir failed.\ntxOutputDir=" + txOutputDir.getAbsolutePath());

            bsqChainStateDir = new File(Paths.get(storageDir.getAbsolutePath(), "all").toString());
            if (!bsqChainStateDir.exists())
                if (!bsqChainStateDir.mkdir())
                    log.warn("make bsqChainStateDir failed.\nbsqChainStateDir=" + bsqChainStateDir.getAbsolutePath());

            txFileManager = new JsonFileManager(txDir);
            txOutputFileManager = new JsonFileManager(txOutputDir);
            bsqChainStateFileManager = new JsonFileManager(bsqChainStateDir);
        }
    }

    public void shutDown() {
        txFileManager.shutDown();
        txOutputFileManager.shutDown();
        bsqChainStateFileManager.shutDown();
    }

    public void maybeExport() {
        if (dumpBlockchainData) {
          /*  List<TxOutputForJson> list = bsqChainState.getVerifiedTxOutputSet().stream()
                    .map(this::getTxOutputForJson)
                    .collect(Collectors.toList());

            list.sort((o1, o2) -> (o1.getSortData().compareTo(o2.getSortData())));
            TxOutputForJson[] array = new TxOutputForJson[list.size()];
            list.toArray(array);*/
            //jsonStorage.queueUpForSave(new PlainTextWrapper(Utilities.objectToJson(array)), 5000);

            ListenableFuture<Void> future = executor.submit(() -> {
                final BsqChainState bsqChainStateClone = bsqChainState.getClone();
                Map<String, Long> burntFeeByTxIdMap = bsqChainStateClone.getBurntFeeByTxIdMap();
                Map<TxIdIndexTuple, SpentInfo> spentInfoByTxOutputMap = bsqChainStateClone.getSpentInfoByTxOutputMap();
                Set<TxOutput> unspentTxOutputSet = bsqChainStateClone.getUnspentTxOutputSet();
                Set<TxOutput> compensationRequestOpReturnTxOutputs = bsqChainStateClone.getCompensationRequestOpReturnTxOutputs();
                Set<TxOutput> votingTxOutputs = bsqChainStateClone.getVotingTxOutputs();
                Set<TxOutput> invalidTxOutputs = bsqChainStateClone.getInvalidatedTxOutputs();
                Set<TxOutput> issuanceTxOutputSet = bsqChainStateClone.getIssuanceBtcTxOutputsByBtcAddressMap()
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());
                String genesisTxId = bsqChainStateClone.getGenesisTxId();

                // map all txs to json txs
                List<TxForJson> txs = new ArrayList<>();
                for (Tx tx : bsqChainStateClone.getTxMap().values()) {
                    String txId = tx.getId();
                    int blockHeight = tx.getBlockHeight();
                    String blockHash = tx.getBlockHash();
                    boolean isGenesisTx = tx.isGenesisTx();

                    //boolean isUnspent = false;
                    boolean isVote = false;
                    boolean isCompensationRequest = false;
                    boolean hasBurnedFee = burntFeeByTxIdMap.containsKey(txId);
                    boolean isIssuance = false;

                    String txType = TxTypeForJson.UNDEFINED.getDisplayString();
                    boolean hasAnyInvalidTxOutput = false;
                    List<TxOutputForJson> outputs = new ArrayList<>();
                    for (TxOutput txOutput : tx.getOutputs()) {
                        // isUnspent = isUnspent || unspentTxOutputSet.contains(txOutput);
                        isVote = isVote || votingTxOutputs.contains(txOutput);
                        isCompensationRequest = isCompensationRequest || compensationRequestOpReturnTxOutputs.contains(txOutput);
                        isIssuance = isIssuance || issuanceTxOutputSet.contains(txOutput);

                        int outputIndex = txOutput.getIndex();
                        final long bsqAmount = -1;//txOutput.getValue();
                        //TODO
                        final long btcAmount = -1;
                        final int height = txOutput.getBlockHeight();

                        final boolean verified = !invalidTxOutputs.contains(txOutput);
                        hasAnyInvalidTxOutput = !verified || hasAnyInvalidTxOutput;
                        final long burntFee = burntFeeByTxIdMap.containsKey(txId) ? burntFeeByTxIdMap.get(txId) : 0;
                        final ScriptPubKeyForJson scriptPubKey = new ScriptPubKeyForJson(txOutput.getPubKeyScript());
                        SpentInfoForJson spentInfoJson = spentInfoByTxOutputMap.containsKey(txOutput.getTxIdIndexTuple()) ?
                                new SpentInfoForJson(spentInfoByTxOutputMap.get(txOutput.getTxIdIndexTuple())) : null;
                        final long time = txOutput.getTime();
                        outputs.add(new TxOutputForJson(txId,
                                outputIndex,
                                bsqAmount,
                                btcAmount,
                                height,
                                verified,
                                burntFee,
                                scriptPubKey,
                                spentInfoJson,
                                time
                        ));
                    }
                    // after iteration of all txOutputs we can evaluate the txType
                    if (txId.equals(genesisTxId))
                        txType = TxTypeForJson.GENESIS.getDisplayString();
                    else if (isVote)
                        txType = TxTypeForJson.VOTE.getDisplayString();
                    else if (isCompensationRequest)
                        txType = TxTypeForJson.COMPENSATION_REQUEST.getDisplayString();
                    else if (hasBurnedFee) // burned fee contains also vote and comp request but we detect those cases above
                        txType = TxTypeForJson.PAY_TRADE_FEE.getDisplayString();
                    else if (isIssuance)
                        txType = TxTypeForJson.ISSUANCE.getDisplayString();
                    else
                        txType = TxTypeForJson.SEND_BSQ.getDisplayString();

                    List<TxInputForJson> inputs = new ArrayList<>();
                    for (TxInput txInput : tx.getInputs()) {
                        int spendingTxOutputIndex = txInput.getSpendingTxOutputIndex();
                        String spendingTxId = txInput.getSpendingTxId();
                        //TODO
                        long bsqAmount = -1;
                        boolean isVerified = !hasAnyInvalidTxOutput;
                        inputs.add(new TxInputForJson(spendingTxOutputIndex,
                                spendingTxId,
                                bsqAmount,
                                isVerified));
                    }

                    // we evaluated during the tx loop so we apply txType here at the end again to have all 
                    // txOutputs the same txType.
                    final String finalTxType = txType;
                    outputs.stream().forEach(txOutputForJson -> {
                        txOutputForJson.setTxType(finalTxType);
                        txOutputFileManager.writeToDisc(Utilities.objectToJson(txOutputForJson), txOutputForJson.getId());
                    });

                    final TxForJson txForJson = new TxForJson(txId,
                            blockHeight,
                            blockHash,
                            inputs,
                            outputs,
                            isGenesisTx,
                            txType);
                    txs.add(txForJson);

                    txFileManager.writeToDisc(Utilities.objectToJson(txForJson), txId);
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
