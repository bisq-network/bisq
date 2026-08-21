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

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.PayoutTxValidation.checkTradePayoutAddressEntry;
import static bisq.core.trade.validation.PayoutTxValidation.checkTradingPeerPayoutAddress;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerSignPayoutTx extends TradeTask {

    public BuyerSignPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getAmount(), "trade.getTradeAmount() must not be null");
            Transaction depositTx = checkNotNull(trade.getDepositTx(),
                    "trade.getDepositTx() must not be null");
            Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            Coin buyerPayoutAmount = offer.getBuyerSecurityDeposit().add(trade.getAmount());
            Coin sellerPayoutAmount = offer.getSellerSecurityDeposit();

            Contract contract = checkNotNull(trade.getContract(), "contract must not be null");

            String buyerPayoutAddressString = checkTradePayoutAddressEntry(contract.getBuyerPayoutAddressString(),
                    walletService,
                    id,
                    "Buyer");

            String sellerPayoutAddressString = checkTradingPeerPayoutAddress(contract.getSellerPayoutAddressString(),
                    processModel.getTradePeer().getPayoutAddressString(),
                    walletService,
                    id,
                    "Seller");

            DeterministicKey buyerMultiSigKeyPair = walletService.getMultiSigKeyPair(id, processModel.getMyMultiSigPubKey());

            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(buyerMultiSigPubKey,
                            walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);
            byte[] sellerMultiSigPubKey = processModel.getTradePeer().getMultiSigPubKey();

            processModel.getTradeWalletService().verifyDepositTxMultiSigOutput(
                    depositTx, buyerMultiSigPubKey, sellerMultiSigPubKey);

            byte[] payoutTxSignature = processModel.getTradeWalletService().buyerSignsPayoutTx(
                    depositTx,
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString,
                    buyerMultiSigKeyPair,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey);
            processModel.setPayoutTxSignature(payoutTxSignature);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
