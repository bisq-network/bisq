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

package io.bitsquare.trade.protocol.trade.tasks.seller;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeTask;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignPayoutTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignPayoutTx.class);

    public SignPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            assert trade.getTradeAmount() != null;
            assert trade.getSecurityDeposit() != null;
            Coin sellerPayoutAmount = trade.getSecurityDeposit();
            Coin buyerPayoutAmount = sellerPayoutAmount.add(trade.getTradeAmount());

            long lockTime = processModel.getTradeWalletService().getLastBlockSeenHeight() + trade.getOffer().getFiatAccountType().lockTimeDelta;
            log.debug("lockTime getLastBlockSeenHeight " + processModel.getTradeWalletService().getLastBlockSeenHeight());
            trade.setLockTime(lockTime);

            byte[] payoutTxSignature = processModel.getTradeWalletService().signPayoutTx(
                    trade.getDepositTx(),
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    processModel.tradingPeer.getPayoutAddressString(),
                    processModel.getAddressEntry(),
                    lockTime,
                    processModel.tradingPeer.getTradeWalletPubKey(),
                    processModel.getTradeWalletPubKey(),
                    processModel.getArbitratorPubKey());

            processModel.setPayoutTxSignature(payoutTxSignature);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}

