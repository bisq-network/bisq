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

package io.bisq.core.dao.blockchain.parse;

import io.bisq.common.app.Version;
import io.bisq.core.dao.blockchain.vo.TxOutput;

import javax.inject.Inject;

public class CompensationRequestVerification {
    private BsqChainState bsqChainState;

    @Inject
    public CompensationRequestVerification(BsqChainState bsqChainState) {
        this.bsqChainState = bsqChainState;
    }

    boolean maybeProcessData(byte[] opReturnData, TxOutput txOutput, long availableValue, int blockHeight, TxOutput btcOutput) {
        if (btcOutput != null &&
                Version.COMPENSATION_REQUEST_VERSION == opReturnData[1] &&
                availableValue == bsqChainState.getCreateCompensationRequestFee(blockHeight) &&
                bsqChainState.isCompensationRequestPeriodValid(blockHeight)) {
            bsqChainState.addCompensationRequestOpReturnOutput(txOutput);
            bsqChainState.adCompensationRequestBtcTxOutputs(btcOutput.getAddress());
            return true;
        }
        return false;
    }
}
