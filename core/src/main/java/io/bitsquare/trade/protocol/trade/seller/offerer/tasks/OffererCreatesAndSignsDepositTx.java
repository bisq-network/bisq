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

package io.bitsquare.trade.protocol.trade.seller.offerer.tasks;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererCreatesAndSignsDepositTx extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererCreatesAndSignsDepositTx.class);

    public OffererCreatesAndSignsDepositTx(TaskRunner taskHandler, Trade offererTrade) {
        super(taskHandler, offererTrade);
    }

    @Override
    protected void doRun() {
        try {
            assert offererTrade.getTradeAmount() != null;
            Coin inputAmount = offererTrade.getSecurityDeposit().add(FeePolicy.TX_FEE).add(offererTrade.getTradeAmount());
            Coin msOutputAmount = inputAmount.add(offererTrade.getSecurityDeposit());

            TradeWalletService.Result result = processModel.getTradeWalletService().createAndSignDepositTx(
                    inputAmount,
                    msOutputAmount,
                    processModel.tradingPeer.getConnectedOutputsForAllInputs(),
                    processModel.tradingPeer.getOutputs(),
                    processModel.getAddressEntry(),
                    processModel.tradingPeer.getTradeWalletPubKey(),
                    processModel.getTradeWalletPubKey(),
                    processModel.getArbitratorPubKey());


            processModel.setConnectedOutputsForAllInputs(result.getConnectedOutputsForAllInputs());
            processModel.setPreparedDepositTx(result.getDepositTx());

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            failed(t);
        }
    }
}
