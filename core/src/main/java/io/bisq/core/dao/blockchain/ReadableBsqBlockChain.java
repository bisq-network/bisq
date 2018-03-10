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

package io.bisq.core.dao.blockchain;

import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import io.bisq.core.dao.blockchain.vo.TxType;
import io.bisq.core.dao.blockchain.vo.util.TxIdIndexTuple;
import org.bitcoinj.core.Coin;

import java.util.*;

public interface ReadableBsqBlockChain {
    int getChainHeadHeight();

    boolean containsBsqBlock(BsqBlock bsqBlock);

    List<BsqBlock> getClonedBlocksFrom(int fromBlockHeight);

    Map<String, Tx> getTxMap();

    Tx getGenesisTx();

    Optional<Tx> getOptionalTx(String txId);

    Set<Tx> getTransactions();

    Set<Tx> getFeeTransactions();

    boolean hasTxBurntFee(String txId);

    String getGenesisTxId();

    int getGenesisBlockHeight();

    boolean containsTx(String txId);

    Optional<TxOutput> getSpendableTxOutput(TxIdIndexTuple txIdIndexTuple);

    Optional<TxOutput> getSpendableTxOutput(String txId, int index);

    boolean isTxOutputSpendable(String txId, int index);

    Set<TxOutput> getUnspentTxOutputs();

    Set<TxOutput> getSpentTxOutputs();

    Optional<TxType> getTxType(String txId);

    boolean isCompensationRequestPeriodValid(int blockHeight);

    long getCreateCompensationRequestFee(int blockHeight);

    Coin getTotalBurntFee();

    Coin getIssuedAmount();

    LinkedList<BsqBlock> getBsqBlocks();

    BsqBlockChain getClone();

    BsqBlockChain getClone(BsqBlockChain bsqBlockChain);

    void printDetails();

    void addListener(BsqBlockChain.Listener listener);

    void removeListener(BsqBlockChain.Listener listener);
}
