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

package io.bitsquare.trade.protocol.trade.buyer.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererCreatesAndSignPayoutTx extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererCreatesAndSignPayoutTx.class);

    public OffererCreatesAndSignPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            assert trade.getTradeAmount() != null;
            Coin securityDeposit = trade.getSecurityDeposit();
            Coin offererPayoutAmount = securityDeposit.add(trade.getTradeAmount());
            Coin takerPayoutAmount = securityDeposit;

            byte[] offererPayoutTxSignature = processModel.getTradeWalletService().createAndSignPayoutTx(
                    trade.getDepositTx(),
                    offererPayoutAmount,
                    takerPayoutAmount,
                    processModel.getAddressEntry(),
                    processModel.tradingPeer.getPayoutAddressString(),
                    processModel.getTradeWalletPubKey(),
                    processModel.tradingPeer.getTradeWalletPubKey(),
                    processModel.getArbitratorPubKey());

            processModel.setPayoutTxSignature(offererPayoutTxSignature);
            processModel.setPayoutAmount(offererPayoutAmount);
            processModel.tradingPeer.setPayoutAmount(takerPayoutAmount);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}

