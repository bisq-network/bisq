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

package bisq.core.trade.protocol.tasks.seller;

import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.governance.param.Param;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerCreatesDelayedPayoutTx extends TradeTask {

    @SuppressWarnings({"unused"})
    public SellerCreatesDelayedPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            String donationAddressString = processModel.getDaoFacade().getParamValue(Param.RECIPIENT_BTC_ADDRESS);
            Coin minerFee = trade.getTxFee();
            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            Transaction depositTx = checkNotNull(trade.getDepositTx());

            long lockTime = trade.getLockTime();
            Transaction preparedDelayedPayoutTx = tradeWalletService.createDelayedUnsignedPayoutTx(depositTx,
                    donationAddressString,
                    minerFee,
                    lockTime);

            processModel.setPreparedDelayedPayoutTx(preparedDelayedPayoutTx);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
