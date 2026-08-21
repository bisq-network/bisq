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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.SetupPayoutTxListener;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.PayoutTxValidation.checkPayoutTx;
import static bisq.core.trade.validation.PayoutTxValidation.checkTradePayoutAddressEntry;
import static bisq.core.trade.validation.PayoutTxValidation.checkTradingPeerPayoutAddress;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerSetupPayoutTxListener extends SetupPayoutTxListener {
    public BuyerSetupPayoutTxListener(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            super.run();

        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected void setState() {
        trade.setStateIfValidTransitionTo(Trade.State.BUYER_SAW_PAYOUT_TX_IN_NETWORK);

        processModel.getTradeManager().requestPersistence();
    }

    @Override
    protected void validatePayoutTx(Transaction payoutTx) {
        BtcWalletService btcWalletService = processModel.getBtcWalletService();
        TradingPeer tradingPeer = processModel.getTradePeer();

        Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");
        Coin tradeAmount = checkNotNull(trade.getAmount(), "tradeAmount must not be null");
        Coin buyerPayoutAmount = offer.getBuyerSecurityDeposit().add(tradeAmount);
        Coin sellerPayoutAmount = offer.getSellerSecurityDeposit();
        Contract contract = checkNotNull(trade.getContract(), "contract must not be null");
        String buyerPayoutAddressString = checkTradePayoutAddressEntry(contract.getBuyerPayoutAddressString(),
                btcWalletService,
                trade.getId(),
                "Buyer");
        String sellerPayoutAddressString = checkTradingPeerPayoutAddress(contract.getSellerPayoutAddressString(),
                tradingPeer.getPayoutAddressString(),
                btcWalletService,
                trade.getId(),
                "Seller");
        Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
        byte[] myMultiSigPubKey = processModel.getMyMultiSigPubKey();
        byte[] peersMultiSigPubKey = tradingPeer.getMultiSigPubKey();
        NetworkParameters params = btcWalletService.getParams();

        checkPayoutTx(payoutTx,
                depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                myMultiSigPubKey,
                peersMultiSigPubKey,
                params);
    }
}
