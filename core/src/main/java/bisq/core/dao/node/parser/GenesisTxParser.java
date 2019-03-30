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

package bisq.core.dao.node.parser;

import bisq.core.dao.node.full.RawTx;
import bisq.core.dao.node.parser.exceptions.InvalidGenesisTxException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class GenesisTxParser {
    public static boolean isGenesis(RawTx rawTx, String genesisTxId, int genesisBlockHeight) {
        return rawTx.getBlockHeight() == genesisBlockHeight && rawTx.getId().equals(genesisTxId);
    }

    public static Tx getGenesisTx(RawTx rawTx, Coin genesisTotalSupply, DaoStateService daoStateService) {
        TempTx genesisTx = getGenesisTempTx(rawTx, genesisTotalSupply);
        commitUTXOs(daoStateService, genesisTx);
        return Tx.fromTempTx(genesisTx);
    }

    private static void commitUTXOs(DaoStateService daoStateService, TempTx genesisTx) {
        ImmutableList<TempTxOutput> outputs = genesisTx.getTempTxOutputs();
        for (int i = 0; i < outputs.size(); ++i) {
            TempTxOutput tempTxOutput = outputs.get(i);
            daoStateService.addUnspentTxOutput(TxOutput.fromTempOutput(tempTxOutput));
        }
    }

    /**
     * Parse and return the genesis transaction for bisq, if applicable.
     *
     * @param rawTx              The candidate transaction.
     * @param genesisTotalSupply The total supply of the genesis issuance for bisq.
     * @return The genesis transaction.
     */
    @VisibleForTesting
    static TempTx getGenesisTempTx(RawTx rawTx, Coin genesisTotalSupply) {
        TempTx tempTx = TempTx.fromRawTx(rawTx);
        tempTx.setTxType(TxType.GENESIS);
        long remainingInputValue = genesisTotalSupply.getValue();
        List<TempTxOutput> tempTxOutputs = tempTx.getTempTxOutputs();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < tempTxOutputs.size(); ++i) {
            TempTxOutput txOutput = tempTxOutputs.get(i);
            long value = txOutput.getValue();
            boolean isValid = value <= remainingInputValue;
            if (!isValid)
                throw new InvalidGenesisTxException("Genesis tx is invalid; using more than available inputs. " +
                        "Remaining input value is " + remainingInputValue + " sat; tx info: " + tempTx.toString());

            remainingInputValue -= value;
            txOutput.setTxOutputType(TxOutputType.GENESIS_OUTPUT);
        }

        if (remainingInputValue > 0) {
            throw new InvalidGenesisTxException("Genesis tx is invalid; not using all available inputs. " +
                    "Remaining input value is " + remainingInputValue + " sat, tx info: " + tempTx.toString());
        }

        return tempTx;
    }
}
