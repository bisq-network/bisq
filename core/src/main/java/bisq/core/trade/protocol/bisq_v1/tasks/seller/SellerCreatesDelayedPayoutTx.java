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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.governance.param.Param;
import bisq.core.trade.DelayedPayoutReceiversUtil;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerCreatesDelayedPayoutTx extends TradeTask {

    public SellerCreatesDelayedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Transaction preparedDelayedPayoutTx;
            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            long lockTime = trade.getLockTime();
            Transaction depositTx = checkNotNull(processModel.getDepositTx());

            if (DelayedPayoutReceiversUtil.isActivated()) {
                long inputAmount = depositTx.getOutput(0).getValue().value;
                List<Tuple2<Long, String>> receivers = DelayedPayoutReceiversUtil.getReceivers(processModel.getDaoFacade(),
                        processModel.getIssuanceList(),
                        inputAmount,
                        trade.getTradeTxFeeAsLong());
                preparedDelayedPayoutTx = tradeWalletService.createDelayedUnsignedPayoutTx(depositTx,
                        receivers,
                        lockTime);
            } else {
                String donationAddressString = processModel.getDaoFacade().getParamValue(Param.RECIPIENT_BTC_ADDRESS);
                Coin minerFee = trade.getTradeTxFee();
                preparedDelayedPayoutTx = tradeWalletService.createDelayedUnsignedPayoutTx(depositTx,
                        donationAddressString,
                        minerFee,
                        lockTime);
            }

            TradeDataValidation.validateDelayedPayoutTx(trade,
                    preparedDelayedPayoutTx,
                    processModel.getDaoFacade(),
                    processModel.getBtcWalletService());
            processModel.setPreparedDelayedPayoutTx(preparedDelayedPayoutTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
