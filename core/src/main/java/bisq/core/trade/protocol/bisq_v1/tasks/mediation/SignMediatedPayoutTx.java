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

package bisq.core.trade.protocol.bisq_v1.tasks.mediation;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

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

    public SignMediatedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradingPeer tradingPeer = processModel.getTradePeer();
            if (processModel.getMediatedPayoutTxSignature() != null) {
                log.warn("processModel.getTxSignatureFromMediation is already set");
            }

            String tradeId = trade.getId();
            BtcWalletService walletService = processModel.getBtcWalletService();
            Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
            Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");
            Coin tradeAmount = checkNotNull(trade.getAmount(), "tradeAmount must not be null");
            Contract contract = checkNotNull(trade.getContract(), "contract must not be null");

            Coin totalPayoutAmount = offer.getBuyerSecurityDeposit().add(tradeAmount).add(offer.getSellerSecurityDeposit());
            Coin buyerPayoutAmount = Coin.valueOf(processModel.getBuyerPayoutAmountFromMediation());
            Coin sellerPayoutAmount = Coin.valueOf(processModel.getSellerPayoutAmountFromMediation());

            checkArgument(totalPayoutAmount.equals(buyerPayoutAmount.add(sellerPayoutAmount)),
                    "Payout amount does not match buyerPayoutAmount=" + buyerPayoutAmount.toFriendlyString() +
                            "; sellerPayoutAmount=" + sellerPayoutAmount);

            boolean isMyRoleBuyer = contract.isMyRoleBuyer(processModel.getPubKeyRing());

            String myPayoutAddressString = walletService.getOrCreateAddressEntry(tradeId, AddressEntry.Context.TRADE_PAYOUT).getAddressString();
            String peersPayoutAddressString = tradingPeer.getPayoutAddressString();
            String buyerPayoutAddressString = isMyRoleBuyer ? myPayoutAddressString : peersPayoutAddressString;
            String sellerPayoutAddressString = isMyRoleBuyer ? peersPayoutAddressString : myPayoutAddressString;

            byte[] myMultiSigPubKey = processModel.getMyMultiSigPubKey();
            byte[] peersMultiSigPubKey = tradingPeer.getMultiSigPubKey();
            byte[] buyerMultiSigPubKey = isMyRoleBuyer ? myMultiSigPubKey : peersMultiSigPubKey;
            byte[] sellerMultiSigPubKey = isMyRoleBuyer ? peersMultiSigPubKey : myMultiSigPubKey;

            DeterministicKey myMultiSigKeyPair = walletService.getMultiSigKeyPair(tradeId, myMultiSigPubKey);

            checkArgument(Arrays.equals(myMultiSigPubKey,
                    walletService.getOrCreateAddressEntry(tradeId, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "myMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + tradeId);

            byte[] mediatedPayoutTxSignature = processModel.getTradeWalletService().signMediatedPayoutTx(
                    depositTx,
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString,
                    myMultiSigKeyPair,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey);
            processModel.setMediatedPayoutTxSignature(mediatedPayoutTxSignature);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

