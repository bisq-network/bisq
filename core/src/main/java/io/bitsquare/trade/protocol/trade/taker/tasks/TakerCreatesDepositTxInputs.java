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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerTrade;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerCreatesDepositTxInputs extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerCreatesDepositTxInputs.class);

    public TakerCreatesDepositTxInputs(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            log.debug("takerTrade.id" + takerTrade.getId());
            Coin inputAmount = takerTrade.getSecurityDeposit().add(FeePolicy.TX_FEE);
            TradeWalletService.Result result = takerTradeProcessModel.getTradeWalletService().createDepositTxInputs(inputAmount,
                    takerTradeProcessModel.taker.getAddressEntry());

            takerTradeProcessModel.taker.setConnectedOutputsForAllInputs(result.getConnectedOutputsForAllInputs());
            takerTradeProcessModel.taker.setOutputs(result.getOutputs());

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}
