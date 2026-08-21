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

package bisq.core.trade.protocol.bisq_v1.tasks.seller_as_maker;

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerSendsInputsForDepositTxResponse;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.Script;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerAsMakerSendsInputsForDepositTxResponse extends MakerSendsInputsForDepositTxResponse {
    public SellerAsMakerSendsInputsForDepositTxResponse(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected byte[] getPreparedDepositTx() {
        Transaction preparedDepositTx = processModel.getBtcWalletService().getTxFromSerializedTx(processModel.getPreparedDepositTx());
        preparedDepositTx.getInputs().forEach(input -> {
            // Remove signature before sending to peer as we don't want to risk that buyer could publish deposit tx
            // before we have received his signature for the delayed payout tx.
            input.setScriptSig(new Script(new byte[]{}));
        });

        processModel.getTradeManager().requestPersistence();

        // Make sure witnesses are removed as well before sending, to cover the segwit case.
        return preparedDepositTx.bitcoinSerialize(false);
    }
}
