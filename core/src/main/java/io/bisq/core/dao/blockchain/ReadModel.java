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
import io.bisq.core.dao.blockchain.vo.util.TxIdIndexTuple;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsulates read access to BsqBlockChain.
 */
public class ReadModel {
    private BsqBlockChain bsqBlockChain;

    @Inject
    public ReadModel(BsqBlockChain bsqBlockChain) {
        this.bsqBlockChain = bsqBlockChain;
    }

    public Optional<TxOutput> getSpendableTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return bsqBlockChain.getSpendableTxOutput(txIdIndexTuple);
    }

    public Optional<TxOutput> getSpendableTxOutput(String txId, int index) {
        return getSpendableTxOutput(new TxIdIndexTuple(txId, index));
    }

    public boolean isCompensationRequestPeriodValid(int blockHeight) {
        return bsqBlockChain.isCompensationRequestPeriodValid(blockHeight);
    }

    public long getCreateCompensationRequestFee(int blockHeight) {
        return bsqBlockChain.getCreateCompensationRequestFee(blockHeight);
    }

    public int getChainHeadHeight() {
        return bsqBlockChain.getChainHeadHeight();
    }

    public String getGenesisTxId() {
        return bsqBlockChain.getGenesisTxId();
    }

    public int getGenesisBlockHeight() {
        return bsqBlockChain.getGenesisBlockHeight();
    }

    public boolean containsBlock(BsqBlock bsqBlock) {
        return bsqBlockChain.containsBlock(bsqBlock);
    }

    public List<BsqBlock> getResetBlocksFrom(int fromBlockHeight) {
        return bsqBlockChain.getResetBlocksFrom(fromBlockHeight);
    }

    public Map<String, Tx> getTxMap() {
        return bsqBlockChain.getTxMap();
    }

    public Tx getGenesisTx() {
        return bsqBlockChain.getGenesisTx();
    }

    public Coin getIssuedAmount() {
        return bsqBlockChain.getIssuedAmount();
    }

    public boolean containsTx(String txId) {
        return bsqBlockChain.containsTx(txId);
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        return bsqBlockChain.isTxOutputSpendable(txId, index);
    }
}
