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

package bisq.restapi;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.provider.price.PriceFeedService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;

import bisq.common.UserThread;

import com.google.inject.Inject;

import com.google.common.io.BaseEncoding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;



import bisq.restapi.dto.JsonBlock;
import bisq.restapi.dto.JsonScriptPubKey;
import bisq.restapi.dto.JsonSpentInfo;
import bisq.restapi.dto.JsonTx;
import bisq.restapi.dto.JsonTxInput;
import bisq.restapi.dto.JsonTxOutput;
import bisq.restapi.dto.JsonTxOutputType;
import bisq.restapi.dto.JsonTxType;

@Getter
@Slf4j
public class DaoExplorerService {
    private final DaoStateService daoStateService;
    private final DaoFacade daoFacade;
    private final DaoStateListener daoStateListener;
    private final Map<String, Set<String>> txIdsByAddress = new HashMap<>();
    @Setter
    private int lastKnownBlockHeight = 0;

    @Inject
    public DaoExplorerService(DaoStateService daoStateService,
                              DaoFacade daoFacade,
                              P2PService p2PService,
                              PriceFeedService priceFeedService) {
        this.daoStateService = daoStateService;
        this.daoFacade = daoFacade;
        daoStateListener = new DaoStateListener() {
            @Override
            public void onParseBlockChainComplete() {
                UserThread.execute(() -> updateTxIdsByAddress());
            }

            @Override
            public void onDaoStateChanged(Block block) {
                UserThread.execute(() -> updateTxIdsByAddress());
            }

        };
        daoFacade.addBsqStateListener(daoStateListener);

        p2PService.addP2PServiceListener(new P2PServiceListener() {
            @Override
            public void onDataReceived() {
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onUpdatedDataReceived() {
            }

            @Override
            public void onTorNodeReady() {
                // We want to get early connected to the price relay so we call it already now
                priceFeedService.setCurrencyCodeOnInit();
                priceFeedService.initialRequestPriceFeed();
            }

            @Override
            public void onHiddenServicePublished() {
            }
        });
    }

    public void updateTxIdsByAddress() {
        Map<TxOutputKey, String> txIdByTxOutputKey = new HashMap<>();
        txIdsByAddress.clear();
        daoStateService.getUnorderedTxStream()
                .forEach(tx -> {
                    tx.getTxOutputs().forEach(txOutput -> {
                        String address = txOutput.getAddress();
                        if (address != null && !address.isEmpty() && daoStateService.isBsqTxOutputType(txOutput)) {
                            Set<String> txIdSet = txIdsByAddress.getOrDefault(address, new HashSet<>());
                            String txId = tx.getId();
                            txIdSet.add(txId);
                            txIdsByAddress.put(address, txIdSet);
                            tx.getTxInputs().forEach(txInput -> {
                                txIdByTxOutputKey.put(txInput.getConnectedTxOutputKey(), txId);
                            });
                        }
                    });
                });
        log.info("txIdByTxOutputKey {}", txIdByTxOutputKey.size());
        // todo check if needed
        daoStateService.getUnorderedTxOutputStream()
                .filter(daoStateService::isBsqTxOutputType)
                .filter(txOutput -> Objects.nonNull(txOutput.getAddress()))
                .forEach(txOutput -> {
                    String txId = txIdByTxOutputKey.get(txOutput.getKey());
                    if (txId != null) {
                        String address = txOutput.getAddress();
                        Set<String> txIdSet = txIdsByAddress.getOrDefault(address, new HashSet<>());
                        txIdSet.add(txId);
                        txIdsByAddress.put(address, txIdSet);
                    }
                });

        log.info("result txIdByTxOutputKey {}", txIdByTxOutputKey.size());
    }

    public JsonBlock getJsonBlock(Block block) {
        List<JsonTx> jsonTxs = block.getTxs().stream()
                .map(this::getJsonTx)
                .collect(Collectors.toList());
        return new JsonBlock(block.getHeight(),
                block.getTime(),
                block.getHash(),
                block.getPreviousBlockHash(),
                jsonTxs);
    }

    public JsonTx getJsonTx(Tx tx) {
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

    public int getNumAddresses() {
        return txIdsByAddress.size();
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

    private String getJsonTxTypeDisplayString(JsonTxType jsonTxType) {
        return jsonTxType != null ? jsonTxType.getDisplayString() : "";
    }

    private JsonTxType getJsonTxType(Tx tx) {
        TxType txType = tx.getTxType();
        return txType != null ? JsonTxType.valueOf(txType.name()) : null;
    }
}
