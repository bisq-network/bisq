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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.OffererTrade;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererCreatesAndSignPayoutTx extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererCreatesAndSignPayoutTx.class);

    public OffererCreatesAndSignPayoutTx(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            assert offererTrade.getTradeAmount() != null;
            Coin securityDeposit = offererTrade.getSecurityDeposit();
            
            Coin offererPayoutAmount = securityDeposit;
            if (offererTrade.getOffer().getDirection() == Offer.Direction.BUY)
                offererPayoutAmount = offererPayoutAmount.add(offererTrade.getTradeAmount());

            Coin takerPayoutAmount = securityDeposit;
            if (offererTrade.getOffer().getDirection() == Offer.Direction.SELL)
                takerPayoutAmount = takerPayoutAmount.add(offererTrade.getTradeAmount());

            byte[] offererPayoutTxSignature = offererTradeProcessModel.getTradeWalletService().createAndSignPayoutTx(
                    offererTrade.getDepositTx(),
                    offererPayoutAmount,
                    takerPayoutAmount,
                    offererTradeProcessModel.offerer.getAddressEntry(),
                    offererTradeProcessModel.taker.getPayoutAddressString(),
                    offererTradeProcessModel.offerer.getTradeWalletPubKey(),
                    offererTradeProcessModel.taker.getTradeWalletPubKey(),
                    offererTradeProcessModel.getArbitratorPubKey());

            offererTradeProcessModel.offerer.setPayoutTxSignature(offererPayoutTxSignature);
            offererTradeProcessModel.offerer.setPayoutAmount(offererPayoutAmount);
            offererTradeProcessModel.taker.setPayoutAmount(takerPayoutAmount);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            failed(t);
        }
    }
}

