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

import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;

import javax.inject.Inject;

/**
 * Encapsulates write access to BsqBlockChain.
 */
public class WriteModel {
    private BsqBlockChain bsqBlockChain;

    @Inject
    public WriteModel(BsqBlockChain bsqBlockChain) {
        this.bsqBlockChain = bsqBlockChain;
    }

    public void removeUnspentTxOutput(TxOutput spendableTxOutput) {
        bsqBlockChain.removeUnspentTxOutput(spendableTxOutput);
    }

    public void addTxToMap(Tx tx) {
        bsqBlockChain.addTxToMap(tx);
    }

    public void addUnspentTxOutput(TxOutput txOutput) {
        bsqBlockChain.addUnspentTxOutput(txOutput);
    }

    public void setGenesisTx(Tx tx) {
        bsqBlockChain.setGenesisTx(tx);
    }

    public void addBlock(BsqBlock bsqBlock) throws BlockNotConnectingException {
        bsqBlockChain.addBlock(bsqBlock);
    }

    public void setCreateCompensationRequestFee(long value, int genesisBlockHeight) {
        bsqBlockChain.setCreateCompensationRequestFee(value, genesisBlockHeight);
    }

    public void setVotingFee(long value, int genesisBlockHeight) {
        bsqBlockChain.setVotingFee(value, genesisBlockHeight);
    }
}
