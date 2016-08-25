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

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignPayoutTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignPayoutTx.class);

    public SignPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
            Coin sellerPayoutAmount = FeePolicy.getSecurityDeposit();
            Coin buyerPayoutAmount = sellerPayoutAmount.add(trade.getTradeAmount());

            // We use the sellers LastBlockSeenHeight, which might be different to the buyers one.
            // If lock time is 0 we set lockTimeAsBlockHeight to 0 to mark it as "not set". 
            // In the tradeWallet we apply the lockTime only if it is set, otherwise we use the default values for 
            // transaction lockTime and sequence number
            long lockTime = trade.getOffer().getPaymentMethod().getLockTime();
            long lockTimeAsBlockHeight = 0;
            if (lockTime > 0)
                lockTimeAsBlockHeight = processModel.getTradeWalletService().getLastBlockSeenHeight() + lockTime;
            trade.setLockTimeAsBlockHeight(lockTimeAsBlockHeight);

            WalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            byte[] payoutTxSignature = processModel.getTradeWalletService().sellerSignsPayoutTx(
                    trade.getDepositTx(),
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    processModel.tradingPeer.getPayoutAddressString(),
                    walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT),
                    walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG),
                    lockTimeAsBlockHeight,
                    processModel.tradingPeer.getMultiSigPubKey(),
                    walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey(),
                    trade.getArbitratorPubKey());

            processModel.setPayoutTxSignature(payoutTxSignature);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

