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
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.TradingPeer;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import java.util.Arrays;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class FinalizeMediatedPayoutTx extends TradeTask {

    @SuppressWarnings({"unused"})
    public FinalizeMediatedPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            String tradeId = trade.getId();
            TradingPeer tradingPeer = processModel.getTradingPeer();
            BtcWalletService walletService = processModel.getBtcWalletService();
            Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");
            Coin tradeAmount = checkNotNull(trade.getTradeAmount(), "tradeAmount must not be null");
            Contract contract = checkNotNull(trade.getContract(), "contract must not be null");

            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");


            byte[] mySignature = checkNotNull(processModel.getTxSignatureFromMediation(),
                    "processModel.getTxSignatureFromMediation must not be null");
            byte[] peersSignature = checkNotNull(tradingPeer.getTxSignatureFromMediation(),
                    "tradingPeer.getTxSignatureFromMediation must not be null");

            PubKeyRing myPubKeyRing = processModel.getPubKeyRing();
            byte[] buyerSignature = contract.isMyRoleBuyer(myPubKeyRing) ? mySignature : peersSignature;
            byte[] sellerSignature = contract.isMyRoleBuyer(myPubKeyRing) ? peersSignature : mySignature;

            Coin totalPayoutAmount = offer.getBuyerSecurityDeposit().add(tradeAmount).add(offer.getSellerSecurityDeposit());
            Coin buyerPayoutAmount = Coin.valueOf(processModel.getBuyerPayoutAmountFromMediation());
            Coin sellerPayoutAmount = Coin.valueOf(processModel.getSellerPayoutAmountFromMediation());
            checkArgument(totalPayoutAmount.equals(buyerPayoutAmount.add(sellerPayoutAmount)),
                    "Payout amount does not match buyerPayoutAmount=" + buyerPayoutAmount.toFriendlyString() +
                            "; sellerPayoutAmount=" + sellerPayoutAmount);

            String buyerPayoutAddressString = tradingPeer.getPayoutAddressString();
            String sellerPayoutAddressString = walletService.getOrCreateAddressEntry(tradeId,
                    AddressEntry.Context.TRADE_PAYOUT).getAddressString();

            byte[] buyerMultiSigPubKey = tradingPeer.getMultiSigPubKey();
            byte[] sellerMultiSigPubKey = processModel.getMyMultiSigPubKey();

            Optional<AddressEntry> MultiSigAddressEntryOptional = walletService.getAddressEntry(tradeId, AddressEntry.Context.MULTI_SIG);
            checkArgument(MultiSigAddressEntryOptional.isPresent() && Arrays.equals(sellerMultiSigPubKey,
                    MultiSigAddressEntryOptional.get().getPubKey()),
                    "sellerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + tradeId);

            DeterministicKey multiSigKeyPair = walletService.getMultiSigKeyPair(tradeId, sellerMultiSigPubKey);

            Transaction transaction = processModel.getTradeWalletService().finalizeMediatedPayoutTx(
                    trade.getDepositTx(),
                    buyerSignature,
                    sellerSignature,
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString,
                    multiSigKeyPair,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey,
                    trade.getArbitratorBtcPubKey()
            );

            trade.setPayoutTx(transaction);

            walletService.swapTradeEntryToAvailableEntry(tradeId, AddressEntry.Context.MULTI_SIG);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

