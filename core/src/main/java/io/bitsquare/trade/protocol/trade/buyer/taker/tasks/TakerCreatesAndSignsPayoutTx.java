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

package io.bitsquare.trade.protocol.trade.buyer.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerCreatesAndSignsPayoutTx extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerCreatesAndSignsPayoutTx.class);

    public TakerCreatesAndSignsPayoutTx(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            assert takerTrade.getTradeAmount() != null;
            Coin securityDeposit = takerTrade.getSecurityDeposit();

            Coin offererPayoutAmount = securityDeposit;
            Coin takerPayoutAmount = securityDeposit.add(takerTrade.getTradeAmount());


            byte[] takerPayoutTxSignature = processModel.getTradeWalletService().createAndSignPayoutTx(
                    takerTrade.getDepositTx(),
                    takerPayoutAmount,
                    offererPayoutAmount,
                    processModel.getAddressEntry(),
                    processModel.tradingPeer.getPayoutAddressString(),
                    processModel.getTradeWalletPubKey(),
                    processModel.tradingPeer.getTradeWalletPubKey(),
                    processModel.getArbitratorPubKey());

            processModel.setPayoutTxSignature(takerPayoutTxSignature);
            processModel.setPayoutAmount(takerPayoutAmount);
            processModel.tradingPeer.setPayoutAmount(offererPayoutAmount);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}

