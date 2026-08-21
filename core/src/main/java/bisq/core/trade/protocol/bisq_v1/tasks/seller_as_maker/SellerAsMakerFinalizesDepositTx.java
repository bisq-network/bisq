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
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsMakerFinalizesDepositTx extends TradeTask {
    public SellerAsMakerFinalizesDepositTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            byte[] takersRawPreparedDepositTx = checkNotNull(processModel.getTradePeer().getPreparedDepositTx());
            byte[] myRawPreparedDepositTx = checkNotNull(processModel.getPreparedDepositTx());
            Transaction takersDepositTx = processModel.getBtcWalletService().getTxFromSerializedTx(takersRawPreparedDepositTx);
            Transaction myDepositTx = processModel.getBtcWalletService().getTxFromSerializedTx(myRawPreparedDepositTx);
            int numTakersInputs = checkNotNull(processModel.getTradePeer().getRawTransactionInputs()).size();
            processModel.getTradeWalletService().sellerAsMakerFinalizesDepositTx(myDepositTx, takersDepositTx, numTakersInputs);

            processModel.setDepositTx(myDepositTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
