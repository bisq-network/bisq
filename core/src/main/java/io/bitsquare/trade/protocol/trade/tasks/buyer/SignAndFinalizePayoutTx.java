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

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignAndFinalizePayoutTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignAndFinalizePayoutTx.class);

    public SignAndFinalizePayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            Coin sellerPayoutAmount = FeePolicy.getSecurityDeposit();
            Coin buyerPayoutAmount = sellerPayoutAmount.add(trade.getTradeAmount());

            Transaction transaction = processModel.getTradeWalletService().buyerSignsAndFinalizesPayoutTx(
                    trade.getDepositTx(),
                    processModel.tradingPeer.getSignature(),
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    processModel.getWalletService().getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.TRADE_PAYOUT),
                    processModel.getWalletService().getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.MULTI_SIG),
                    processModel.tradingPeer.getPayoutAddressString(),
                    trade.getLockTimeAsBlockHeight(),
                    processModel.getWalletService().getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.MULTI_SIG).getPubKey(),
                    processModel.tradingPeer.getMultiSigPubKey(),
                    trade.getArbitratorPubKey()
            );

            trade.setPayoutTx(transaction);
            trade.setState(Trade.State.BUYER_COMMITTED_PAYOUT_TX);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
