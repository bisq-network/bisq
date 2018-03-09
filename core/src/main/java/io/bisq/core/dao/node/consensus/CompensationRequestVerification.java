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

package io.bisq.core.dao.node.consensus;

import io.bisq.common.app.Version;
import io.bisq.core.dao.blockchain.BsqBlockChain;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import io.bisq.core.dao.blockchain.vo.TxOutputType;
import io.bisq.core.dao.blockchain.vo.TxType;

import javax.inject.Inject;

/**
 * Verifies if a given transaction is a CompensationRequest OP_RETURN transaction.
 */
public class CompensationRequestVerification {
    private final BsqBlockChain bsqBlockChain;

    @Inject
    public CompensationRequestVerification(BsqBlockChain bsqBlockChain) {
        this.bsqBlockChain = bsqBlockChain;
    }

    public boolean verify(byte[] opReturnData, long bsqFee, int blockHeight, TxOutput btcTxOutput) {
        return btcTxOutput != null &&
                opReturnData.length == 22 &&
                Version.COMPENSATION_REQUEST_VERSION == opReturnData[1] &&
                bsqFee == bsqBlockChain.getCreateCompensationRequestFee(blockHeight) &&
                bsqBlockChain.isCompensationRequestPeriodValid(blockHeight);
    }

    public void applyStateChange(Tx tx, TxOutput opReturnTxOutput, TxOutput btcTxOutput) {
        opReturnTxOutput.setTxOutputType(TxOutputType.COMPENSATION_REQUEST_OP_RETURN_OUTPUT);
        btcTxOutput.setTxOutputType(TxOutputType.COMPENSATION_REQUEST_ISSUANCE_CANDIDATE_OUTPUT);
        tx.setTxType(TxType.COMPENSATION_REQUEST);
    }
}
