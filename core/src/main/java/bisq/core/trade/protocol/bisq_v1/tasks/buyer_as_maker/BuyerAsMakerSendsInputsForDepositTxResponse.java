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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_maker;

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerSendsInputsForDepositTxResponse;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerAsMakerSendsInputsForDepositTxResponse extends MakerSendsInputsForDepositTxResponse {
    public BuyerAsMakerSendsInputsForDepositTxResponse(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected byte[] getPreparedDepositTx() {
        Transaction preparedDepositTx = processModel.getBtcWalletService().getTxFromSerializedTx(processModel.getPreparedDepositTx());
        // Remove witnesses from preparedDepositTx, so that the seller can still compute the final
        // tx id, but cannot publish it before providing the buyer with a signed delayed payout tx.
        return preparedDepositTx.bitcoinSerialize(false);
    }
}
