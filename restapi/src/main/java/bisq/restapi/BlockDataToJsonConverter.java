package bisq.restapi;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;

import com.google.common.io.BaseEncoding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;



import bisq.restapi.dto.JsonBlock;
import bisq.restapi.dto.JsonScriptPubKey;
import bisq.restapi.dto.JsonSpentInfo;
import bisq.restapi.dto.JsonTx;
import bisq.restapi.dto.JsonTxInput;
import bisq.restapi.dto.JsonTxOutput;
import bisq.restapi.dto.JsonTxOutputType;
import bisq.restapi.dto.JsonTxType;

@Slf4j
public class BlockDataToJsonConverter {
    public static JsonBlock getJsonBlock(DaoStateService daoStateService, Block block) {
        List<JsonTx> jsonTxs = block.getTxs().stream()
                .map(tx -> getJsonTx(daoStateService, tx))
                .collect(Collectors.toList());
        return new JsonBlock(block.getHeight(),
                block.getTime(),
                block.getHash(),
                block.getPreviousBlockHash(),
                jsonTxs);
    }

    public static JsonTx getJsonTx(DaoStateService daoStateService, Tx tx) {
        JsonTxType jsonTxType = getJsonTxType(tx);
        String jsonTxTypeDisplayString = getJsonTxTypeDisplayString(jsonTxType);
        return new JsonTx(tx.getId(),
                tx.getBlockHeight(),
                tx.getBlockHash(),
                tx.getTime(),
                getJsonTxInputs(daoStateService, tx),
                getJsonTxOutputs(daoStateService, tx),
                jsonTxType,
                jsonTxTypeDisplayString,
                tx.getBurntFee(),
                tx.getInvalidatedBsq(),
                tx.getUnlockBlockHeight());
    }

    private static List<JsonTxInput> getJsonTxInputs(DaoStateService daoStateService, Tx tx) {
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

    private static List<JsonTxOutput> getJsonTxOutputs(DaoStateService daoStateService, Tx tx) {
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
                    BaseEncoding HEX = BaseEncoding.base16().lowerCase();
                    String opReturn = txOutput.getOpReturnData() != null ? HEX.encode(txOutput.getOpReturnData()) : null;
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

    private static String getJsonTxTypeDisplayString(JsonTxType jsonTxType) {
        return jsonTxType != null ? jsonTxType.getDisplayString() : "";
    }

    private static JsonTxType getJsonTxType(Tx tx) {
        TxType txType = tx.getTxType();
        return txType != null ? JsonTxType.valueOf(txType.name()) : null;
    }
}
