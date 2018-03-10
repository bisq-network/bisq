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

public interface WritableBsqBlockChain {

    void applySnapshot(BsqBlockChain snapshot);

    void addBlock(BsqBlock bsqBlock);

    void setGenesisTx(Tx tx);

    void addTxToMap(Tx tx);

    void addUnspentTxOutput(TxOutput txOutput);

    void removeUnspentTxOutput(TxOutput spendableTxOutput);

    void setCreateCompensationRequestFee(long value, int genesisBlockHeight);

    void setVotingFee(long value, int genesisBlockHeight);

    void addListener(BsqBlockChain.Listener listener);

    void removeListener(BsqBlockChain.Listener listener);
}
