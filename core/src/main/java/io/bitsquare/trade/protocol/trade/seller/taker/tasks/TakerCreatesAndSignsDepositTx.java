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

package io.bitsquare.trade.protocol.trade.seller.taker.tasks;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerCreatesAndSignsDepositTx extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerCreatesAndSignsDepositTx.class);

    public TakerCreatesAndSignsDepositTx(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            assert takerTrade.getTradeAmount() != null;
            Coin inputAmount = takerTrade.getSecurityDeposit().add(FeePolicy.TX_FEE).add(takerTrade.getTradeAmount());
            Coin msOutputAmount = inputAmount.add(takerTrade.getSecurityDeposit());
            
            TradeWalletService.Result result = takerTradeProcessModel.getTradeWalletService().createAndSignDepositTx(
                    inputAmount,
                    msOutputAmount,
                    takerTradeProcessModel.tradingPeer.getConnectedOutputsForAllInputs(),
                    takerTradeProcessModel.tradingPeer.getOutputs(),
                    takerTradeProcessModel.getAddressEntry(),
                    takerTradeProcessModel.tradingPeer.getTradeWalletPubKey(),
                    takerTradeProcessModel.getTradeWalletPubKey(),
                    takerTradeProcessModel.getArbitratorPubKey());

            takerTradeProcessModel.setConnectedOutputsForAllInputs(result.getConnectedOutputsForAllInputs());
            takerTradeProcessModel.setPreparedDepositTx(result.getDepositTx());

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}
