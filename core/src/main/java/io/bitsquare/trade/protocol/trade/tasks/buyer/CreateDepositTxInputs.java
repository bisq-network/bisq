/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.tasks.buyer;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import io.bitsquare.trade.states.StateUtil;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDepositTxInputs extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(CreateDepositTxInputs.class);

    public CreateDepositTxInputs(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("trade.id" + trade.getId());
            Coin inputAmount = trade.getSecurityDeposit().add(FeePolicy.TX_FEE);
            TradeWalletService.Result result = processModel.getTradeWalletService().createDepositTxInputs(inputAmount,
                    processModel.getAddressEntry());

            processModel.setConnectedOutputsForAllInputs(result.getConnectedOutputsForAllInputs());
            processModel.setOutputs(result.getOutputs());

            complete();
        } catch (Throwable t) {
            t.printStackTrace();

            StateUtil.setOfferOpenState(trade);

            failed(t);
        }
    }
}
