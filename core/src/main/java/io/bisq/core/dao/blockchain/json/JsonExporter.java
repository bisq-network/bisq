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
import io.bisq.common.storage.PlainTextWrapper;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.RpcOptionKeys;
import io.bisq.core.dao.blockchain.btcd.PubKeyScript;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.blockchain.vo.SpentInfo;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import java.io.File;

@Slf4j
public class JsonExporter {
    private final Storage<PlainTextWrapper> jsonStorage;
    private final boolean dumpBlockchainData;
    private final File storageDir;
    private final ListeningExecutorService executor = Utilities.getListeningExecutorService("JsonExporter", 1, 1, 1200);
    private BsqChainState bsqChainState;

    @Inject
    public JsonExporter(Storage<PlainTextWrapper> jsonStorage,
                        BsqChainState bsqChainState,
                        @Named(Storage.STORAGE_DIR) File storageDir,
                        @Named(RpcOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.bsqChainState = bsqChainState;
        this.storageDir = storageDir;
        this.jsonStorage = jsonStorage;
        this.dumpBlockchainData = dumpBlockchainData;

        if (dumpBlockchainData)
            this.jsonStorage.initWithFileName("bsqChainState.json");
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
                jsonStorage.queueUpForSave(new PlainTextWrapper(Utilities.objectToJson(bsqChainState.getClone())), 5000);
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
    }

    private TxOutputForJson getTxOutputForJson(TxOutput txOutput) {
        String txId = txOutput.getTxId();
        int outputIndex = txOutput.getIndex();
        final long bsqAmount = txOutput.getValue();
        final int height = txOutput.getBlockHeight();
        
       /* final boolean isBsqCoinBase = txOutput.isIssuanceOutput();
        final boolean verified = txOutput.isVerified();
        final long burnedFee = txOutput.getBurnedFee();
        final long btcTxFee = txOutput.getBtcTxFee();*/

        // TODO
        final boolean isBsqCoinBase = false;
        final boolean verified = true;
        final long burnedFee = 0;
        final long btcTxFee = 0;

        PubKeyScript pubKeyScript = txOutput.getPubKeyScript();
        final ScriptPubKeyForJson scriptPubKey = new ScriptPubKeyForJson(pubKeyScript.getAddresses(),
                pubKeyScript.getAsm(),
                pubKeyScript.getHex(),
                pubKeyScript.getReqSigs(),
                pubKeyScript.getType().toString());
        SpentInfoForJson spentInfoJson = null;
        // SpentInfo spentInfo = txOutput.getSpentInfo();
        SpentInfo spentInfo = null;
        if (spentInfo != null)
            spentInfoJson = new SpentInfoForJson(spentInfo.getBlockHeight(),
                    spentInfo.getInputIndex(),
                    spentInfo.getTxId());

        final long time = txOutput.getTime();
        final String txVersion = "";//txOutput.getTxVersion();
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

}
