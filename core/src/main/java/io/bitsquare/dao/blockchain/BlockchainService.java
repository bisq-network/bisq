/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.blockchain;

import com.google.inject.Inject;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

abstract public class BlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    // regtest
    public static final String GENESIS_TX_ID = "aaecf8666c4d6680b952a1f95a5bc4e0fb958dae992d445df760d6162a131c61";
    public static final int GENESIS_BLOCK_HEIGHT = 700; //at regtest: 700,  447000

    protected Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap;
    private List<UtxoListener> utxoListeners = new ArrayList<>();
    private boolean isUtxoAvailable;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlockchainService() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        setup(this::setupComplete, errorMessage -> {
            log.error("setup failed" + errorMessage);
        });
    }

    public Map<String, Map<Integer, SquUTXO>> getUtxoByTxIdMap() {
        return utxoByTxIdMap;
    }


    public void addUtxoListener(UtxoListener utxoListener) {
        utxoListeners.add(utxoListener);
    }

    public void removeUtxoListener(UtxoListener utxoListener) {
        utxoListeners.remove(utxoListener);
    }

    public boolean isUtxoAvailable() {
        return isUtxoAvailable;
    }
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setupComplete() {
        syncFromGenesis(utxoByTxIdMap -> {
            this.utxoByTxIdMap = utxoByTxIdMap;
            isUtxoAvailable = true;
            utxoListeners.stream().forEach(e -> e.onUtxoChanged(utxoByTxIdMap));
            syncFromGenesisCompete();
        }, errorMessage -> {
            log.error("syncFromGenesis failed" + errorMessage);
        });
    }

    abstract protected void syncFromGenesisCompete();

    abstract protected void setup(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler);

    abstract protected void syncFromGenesis(Consumer<Map<String, Map<Integer, SquUTXO>>> resultHandler, ErrorMessageHandler errorMessageHandler);

    abstract void parseBlockchainFromGenesis(Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap, int genesisBlockHeight, String genesisTxId) throws BlockchainException;

    abstract SquTransaction getSquTransaction(String txId) throws BlockchainException;

    void parseBlock(SquBlock block, String genesisTxId, Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap) {
        int blockHeight = block.blockHeight;
        log.debug("Parse block at height={} ", blockHeight);
        for (String txId : block.txIds) {
            parseTransaction(txId, genesisTxId, blockHeight, utxoByTxIdMap);
        }
    }

    SquTransaction parseTransaction(String txId, String genesisTxId, int blockHeight, Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap) {
        log.debug("Parse transaction with id={} ", txId);
        try {
            SquTransaction squTransaction = getSquTransaction(txId);
            List<SquTxOutput> outputs = squTransaction.outputs;
            if (txId.equals(genesisTxId)) {
                // Genesis tx uses all outputs as SQU outputs
                Map<Integer, SquUTXO> utxoByIndexMap = new HashMap<>();
                for (int i = 0; i < outputs.size(); i++) {
                    SquTxOutput output = outputs.get(i);
                    List<String> addresses = output.addresses;
                    // Only at raw MS outputs addresses have more then 1 entry 
                    // We do not support raw MS for SQU
                    if (addresses.size() == 1) {
                        String address = addresses.get(0);
                        SquUTXO utxo = new SquUTXO(Sha256Hash.of(Utils.HEX.decode(txId)),
                                output.index,
                                output.value,
                                blockHeight,
                                true,
                                output.script,
                                address);
                        utxoByIndexMap.put(i, utxo);
                    }
                }
                checkArgument(!utxoByIndexMap.isEmpty(), "Genesis tx must have squ utxo");
                boolean wasEmpty = utxoByTxIdMap.put(txId, utxoByIndexMap) == null;
                checkArgument(wasEmpty, "We must not have that tx in the map. txId=" + txId);
            } else if (isIssuanceTx(txId)) {
                // Issuance tx has the funding output at index 0 and the SQU output at index 1
                if (outputs.size() > 1) {
                    if (isValidFundingOutput(outputs.get(0))) {
                        // We have a valid funding tx so we add out issuance output to the utxo
                        SquTxOutput squOutput = outputs.get(1);
                        if (isValidIssuanceOutput(squOutput)) {
                            List<String> addresses = squOutput.addresses;
                            // Only at raw MS outputs addresses have more then 1 entry 
                            // We do not support raw MS for SQU
                            if (addresses.size() == 1) {
                                String address = addresses.get(0);
                                SquUTXO utxo = new SquUTXO(Sha256Hash.of(Utils.HEX.decode(txId)),
                                        squOutput.index,
                                        squOutput.value,
                                        blockHeight,
                                        true,
                                        squOutput.script,
                                        address);
                                Map<Integer, SquUTXO> utxoByIndexMap = new HashMap<>();
                                utxoByIndexMap.put(1, utxo);
                                boolean wasEmpty = utxoByTxIdMap.put(txId, utxoByIndexMap) == null;
                                checkArgument(wasEmpty, "We must not have that tx in the map. txId=" + txId);
                            }
                        }
                    }
                }
            } else {
                // Other txs
                Coin availableValue = Coin.ZERO;
                for (SquTxInput input : squTransaction.inputs) {
                    String spendingTxId = input.spendingTxId;
                    if (utxoByTxIdMap.containsKey(spendingTxId)) {
                        Map<Integer, SquUTXO> utxoByIndexMap = utxoByTxIdMap.get(spendingTxId);
                        Integer index = input.index;
                        if (utxoByIndexMap.containsKey(index)) {
                            SquUTXO utxo = utxoByIndexMap.get(index);
                            availableValue = availableValue.add(utxo.getValue());
                            utxoByIndexMap.remove(index);
                            if (utxoByIndexMap.isEmpty()) {
                                // If no more entries by index we can remove the whole entry by txId
                                utxoByTxIdMap.remove(spendingTxId);
                            }
                        }
                    }
                }
                // If we have an input spending tokens we iterate the outputs
                if (availableValue.isPositive()) {
                    Map<Integer, SquUTXO> utxoByIndexMap = utxoByTxIdMap.containsKey(txId) ?
                            utxoByTxIdMap.get(txId) :
                            new HashMap<>();
                    // We sort by index, inputs are tokens as long there is enough input value
                    for (int i = 0; i < outputs.size(); i++) {
                        SquTxOutput squOutput = outputs.get(i);
                        List<String> addresses = squOutput.addresses;
                        // Only at raw MS outputs addresses have more then 1 entry 
                        // We do not support raw MS for SQU
                        if (addresses.size() == 1) {
                            String address = addresses.get(0);
                            availableValue = availableValue.subtract(squOutput.value);
                            if (!availableValue.isNegative()) {
                                // We are spending available tokens
                                SquUTXO utxo = new SquUTXO(Sha256Hash.of(Utils.HEX.decode(txId)),
                                        squOutput.index,
                                        squOutput.value,
                                        blockHeight,
                                        false,
                                        squOutput.script,
                                        address);
                                utxoByIndexMap.put(i, utxo);
                            } else {
                                // As soon we have spent our inputs we can break
                                break;
                            }
                        }
                    }
                    if (!utxoByIndexMap.isEmpty() && !utxoByTxIdMap.containsKey(txId)) {
                        boolean wasEmpty = utxoByTxIdMap.put(txId, utxoByIndexMap) == null;
                        checkArgument(wasEmpty, "We must not have that tx in the map. txId=" + txId);
                    }
                }
            }

            if (isRequestPhase(blockHeight)) {

              /*  Check if tx is in time interval of phase 1
                Check if OP_RETURN data starts with version byte and has latest version
                Check if burned SQU have been mature
                Check if SQU is a valid SQU input
                Check if fee payment is correct
                Check if there is min. 1 Btc output (small payment to own compensation receiving address)
                */
            } else if (isVotingPhase(blockHeight)) {

            } else if (isFundingPhase(blockHeight)) {
            }
            return squTransaction;
        } catch (BlockchainException e) {
            //TODO throw
            log.error(e.toString());
            return null;
        }
    }


    boolean isValidIssuanceOutput(SquTxOutput output) {
        //TODO
        return true;
    }

    boolean isValidFundingOutput(SquTxOutput output) {
        //TODO
        return true;
    }

    boolean isIssuanceTx(String txId) {
        // TODO
        return false;
    }

    boolean isRequestPhase(int blockHeight) {
        return false;
    }

    boolean isVotingPhase(int blockHeight) {
        return false;
    }

    boolean isFundingPhase(int blockHeight) {
        return false;
    }


    protected void printUtxoMap(Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap) {
        StringBuilder sb = new StringBuilder("utxoByTxIdMap:\n");
        utxoByTxIdMap.entrySet().stream().forEach(e -> {
            sb.append("TxId: ").append(e.getKey()).append("\n");
            e.getValue().entrySet().stream().forEach(a -> {
                sb.append("    [").append(a.getKey()).append("] {")
                        .append(a.getValue().toString()).append("}\n");
            });
        });
        log.info(sb.toString());
    }
}
