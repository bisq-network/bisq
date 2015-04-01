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

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerTrade;

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

          
            byte[] takerPayoutTxSignature = takerTradeProcessModel.getTradeWalletService().createAndSignPayoutTx(
                    takerTrade.getDepositTx(),
                    takerPayoutAmount,
                    offererPayoutAmount,
                    takerTradeProcessModel.taker.getAddressEntry(),
                    takerTradeProcessModel.offerer.getPayoutAddressString(),
                    takerTradeProcessModel.taker.getTradeWalletPubKey(),
                    takerTradeProcessModel.offerer.getTradeWalletPubKey(),
                    takerTradeProcessModel.getArbitratorPubKey());

            takerTradeProcessModel.taker.setPayoutTxSignature(takerPayoutTxSignature);
            takerTradeProcessModel.taker.setPayoutAmount(takerPayoutAmount);
            takerTradeProcessModel.offerer.setPayoutAmount(offererPayoutAmount);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}

