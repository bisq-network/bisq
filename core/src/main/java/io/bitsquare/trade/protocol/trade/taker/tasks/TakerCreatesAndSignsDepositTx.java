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
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.taker.models.TakerAsSellerModel;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerCreatesAndSignsDepositTx extends Task<TakerAsSellerModel> {
    private static final Logger log = LoggerFactory.getLogger(TakerCreatesAndSignsDepositTx.class);

    public TakerCreatesAndSignsDepositTx(TaskRunner taskHandler, TakerAsSellerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            Coin takerInputAmount = model.trade.getTradeAmount().add(model.trade.getSecurityDeposit()).add(FeePolicy.TX_FEE);
            Coin msOutputAmount = takerInputAmount.add(model.trade.getSecurityDeposit());

            TradeWalletService.Result result = model.tradeWalletService.takerCreatesAndSignsDepositTx(
                    takerInputAmount,
                    msOutputAmount,
                    model.offerer.connectedOutputsForAllInputs,
                    model.offerer.outputs,
                    model.taker.addressEntry,
                    model.offerer.tradeWalletPubKey,
                    model.taker.tradeWalletPubKey,
                    model.arbitratorPubKey);


            model.taker.connectedOutputsForAllInputs = result.getConnectedOutputsForAllInputs();
            model.taker.outputs = result.getOutputs();
            model.taker.preparedDepositTx = result.getDepositTx();

            complete();
        } catch (Exception e) {
            Trade.ProcessState processState = Trade.ProcessState.FAULT;
            processState.setErrorMessage(errorMessage);
            model.trade.setProcessState(processState);

            failed(e);
        }
    }
}
