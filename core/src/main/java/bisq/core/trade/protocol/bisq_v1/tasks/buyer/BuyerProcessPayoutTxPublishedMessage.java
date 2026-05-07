/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.account.sign.SignedWitness;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.PayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.PayoutTxValidation.checkPayoutTx;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerProcessPayoutTxPublishedMessage extends TradeTask {
    public BuyerProcessPayoutTxPublishedMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            PayoutTxPublishedMessage message = (PayoutTxPublishedMessage) processModel.getTradeMessage();
            checkNotNull(message);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            if (trade.getPayoutTx() == null) {
                BtcWalletService btcWalletService = processModel.getBtcWalletService();
                Wallet wallet = btcWalletService.getWallet();
                TradingPeer tradingPeer = processModel.getTradePeer();

                Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");
                Coin tradeAmount = checkNotNull(trade.getAmount(), "tradeAmount must not be null");
                Coin buyerPayoutAmount = offer.getBuyerSecurityDeposit().add(tradeAmount);
                Coin sellerPayoutAmount = offer.getSellerSecurityDeposit();
                String buyerPayoutAddressString = btcWalletService.getOrCreateAddressEntry(trade.getId(),
                        AddressEntry.Context.TRADE_PAYOUT).getAddressString();
                String sellerPayoutAddressString = tradingPeer.getPayoutAddressString();
                Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
                byte[] myMultiSigPubKey = processModel.getMyMultiSigPubKey();
                byte[] peersMultiSigPubKey = tradingPeer.getMultiSigPubKey();
                byte[] serializedPayoutTx = message.getPayoutTx();

                byte[] checkedPayoutTx = checkPayoutTx(serializedPayoutTx,
                        btcWalletService,
                        depositTx,
                        buyerPayoutAmount,
                        sellerPayoutAmount,
                        buyerPayoutAddressString,
                        sellerPayoutAddressString,
                        myMultiSigPubKey,
                        peersMultiSigPubKey);

                Transaction committedPayoutTx = WalletService.maybeAddNetworkTxToWallet(checkedPayoutTx, wallet);
                trade.setPayoutTx(committedPayoutTx);
                BtcWalletService.printTx("payoutTx received from peer", committedPayoutTx);

                trade.setState(Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG);
                btcWalletService.resetCoinLockedInMultiSigAddressEntry(trade.getId());
            } else {
                log.info("We got the payout tx already set from BuyerSetupPayoutTxListener and do nothing here. trade ID={}", trade.getId());
            }

            SignedWitness signedWitness = message.getSignedWitness();
            if (signedWitness != null) {
                // We received the signedWitness from the seller and publish the data to the network.
                // The signer has published it as well but we prefer to re-do it on our side as well to achieve higher
                // resilience.
                processModel.getAccountAgeWitnessService().publishOwnSignedWitness(signedWitness);
            }

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
