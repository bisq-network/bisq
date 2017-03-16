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

package io.bisq.trade.protocol.trade.tasks.buyer;

import io.bisq.btc.AddressEntry;
import io.bisq.btc.wallet.BtcWalletService;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.p2p.protocol.availability.Offer;
import io.bisq.trade.Trade;
import io.bisq.trade.protocol.trade.TradingPeer;
import io.bisq.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class SignAndFinalizePayoutTx extends TradeTask {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(SignAndFinalizePayoutTx.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SignAndFinalizePayoutTx(TaskRunner taskHandler, Trade trade) {
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
            Transaction transaction = processModel.getTradeWalletService().buyerSignsAndFinalizesPayoutTx(
                    trade.getDepositTx(),
                    tradingPeer.getSignature(),
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    tradingPeer.getPayoutAddressString(),
                    multiSigKeyPair,
                    trade.getLockTimeAsBlockHeight(),
                    buyerMultiSigPubKey,
                    tradingPeer.getMultiSigPubKey(),
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
