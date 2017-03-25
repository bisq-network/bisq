/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.buyer_as_taker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.TradingPeer;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerAsTakerSignAndFinalizePayoutTx extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public BuyerAsTakerSignAndFinalizePayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            Offer offer = trade.getOffer();
            Coin sellerPayoutAmount = offer.getSellerSecurityDeposit();
            Coin buyerPayoutAmount = offer.getBuyerSecurityDeposit().add(trade.getTradeAmount());
            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            String buyerPayoutAddressString = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT).getAddressString();
            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            DeterministicKey multiSigKeyPair = walletService.getMultiSigKeyPair(id, buyerMultiSigPubKey);
            TradingPeer tradingPeer = processModel.tradingPeer;
            //TODO: locktime  
            Transaction transaction = processModel.getTradeWalletService().buyerSignsAndFinalizesPayoutTx(
                    trade.getDepositTx(),
                    tradingPeer.getSignature(),
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    tradingPeer.getPayoutAddressString(),
                    multiSigKeyPair,
                   /* trade.getLockTimeAsBlockHeight(),*/
                    buyerMultiSigPubKey,
                    tradingPeer.getMultiSigPubKey(),
                    trade.getArbitratorPubKey()
            );

            trade.setPayoutTx(transaction);
            trade.setState(Trade.State.BUYER_AS_TAKER_COMMITTED_PAYOUT_TX);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
