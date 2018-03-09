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

package io.bisq.core.dao.node.consensus;

import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.dao.blockchain.BsqBlockChainWriteModel;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxType;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Verifies if a given transaction is a BSQ genesis transaction.
 */
public class GenesisTxController {

    private final BsqBlockChainWriteModel bsqBlockChainWriteModel;
    private final String genesisTxId;
    private final int genesisBlockHeight;

    @Inject
    public GenesisTxController(BsqBlockChainWriteModel bsqBlockChainWriteModel,
                               @Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                               @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.bsqBlockChainWriteModel = bsqBlockChainWriteModel;
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
    }

    public boolean isGenesisTx(Tx tx, int blockHeight) {
        return tx.getId().equals(genesisTxId) && blockHeight == genesisBlockHeight;
    }

    public void applyStateChange(Tx tx) {
        tx.getOutputs().forEach(txOutput -> {
            txOutput.setUnspent(true);
            txOutput.setVerified(true);
            bsqBlockChainWriteModel.addUnspentTxOutput(txOutput);
        });
        tx.setTxType(TxType.GENESIS);

        bsqBlockChainWriteModel.setGenesisTx(tx);
        bsqBlockChainWriteModel.addTxToMap(tx);
    }
}
