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

package bisq.core.trade.protocol.tasks.mediation;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.TradingPeer;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SignMediatedPayoutTx extends TradeTask {

    @SuppressWarnings({"unused"})
    public SignMediatedPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradingPeer tradingPeer = processModel.getTradingPeer();
            checkArgument(processModel.getTxSignatureFromMediation() == null,
                    "processModel.getTxSignatureFromMediation is already set");

            String tradeId = trade.getId();
            Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
            Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");
            Coin tradeAmount = checkNotNull(trade.getTradeAmount(), "tradeAmount must not be null");

            BtcWalletService walletService = processModel.getBtcWalletService();

            Coin totalPayoutAmount = offer.getBuyerSecurityDeposit().add(tradeAmount).add(offer.getSellerSecurityDeposit());
            Coin buyerPayoutAmount = Coin.valueOf(processModel.getBuyerPayoutAmountFromMediation());
            Coin sellerPayoutAmount = Coin.valueOf(processModel.getSellerPayoutAmountFromMediation());
            checkArgument(totalPayoutAmount.equals(buyerPayoutAmount.add(sellerPayoutAmount)),
                    "Payout amount does not match buyerPayoutAmount=" + buyerPayoutAmount.toFriendlyString() +
                            "; sellerPayoutAmount=" + sellerPayoutAmount);

            String buyerPayoutAddressString = walletService.getOrCreateAddressEntry(tradeId,
                    AddressEntry.Context.TRADE_PAYOUT).getAddressString();
            String sellerPayoutAddressString = tradingPeer.getPayoutAddressString();

            DeterministicKey buyerMultiSigKeyPair = walletService.getMultiSigKeyPair(tradeId, processModel.getMyMultiSigPubKey());

            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(buyerMultiSigPubKey,
                    walletService.getOrCreateAddressEntry(tradeId, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + tradeId);
            byte[] sellerMultiSigPubKey = tradingPeer.getMultiSigPubKey();

            byte[] payoutTxSignature = processModel.getTradeWalletService().signMediatedPayoutTx(
                    depositTx,
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString,
                    buyerMultiSigKeyPair,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey,
                    trade.getArbitratorBtcPubKey());
            processModel.setPayoutTxSignature(payoutTxSignature);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

