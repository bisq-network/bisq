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

package io.bisq.core.trade.protocol.tasks.buyer_as_maker;

import com.google.common.base.Preconditions;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.DeterministicKey;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BuyerAsMakerSignPayoutTx extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public BuyerAsMakerSignPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Preconditions.checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            Preconditions.checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");

            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();

            Coin buyerPayoutAmount = trade.getOffer().getBuyerSecurityDeposit().add(trade.getTradeAmount());
            Coin sellerPayoutAmount = trade.getOffer().getSellerSecurityDeposit();

            String buyerPayoutAddressString = walletService.getOrCreateAddressEntry(id,
                    AddressEntry.Context.TRADE_PAYOUT).getAddressString();
            final String sellerPayoutAddressString = processModel.tradingPeer.getPayoutAddressString();

            DeterministicKey buyerMultiSigKeyPair = walletService.getMultiSigKeyPair(id, processModel.getMyMultiSigPubKey());

            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(buyerMultiSigPubKey,
                            walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);
            final byte[] sellerMultiSigPubKey = processModel.tradingPeer.getMultiSigPubKey();

            byte[] payoutTxSignature = processModel.getTradeWalletService().buyerSignsPayoutTx(
                    trade.getDepositTx(),
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString,
                    buyerMultiSigKeyPair,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey,
                    trade.getArbitratorPubKey());

          /*  
            DeterministicKey multiSigKeyPair,
            byte[] buyerPubKey,
            byte[] sellerPubKey,
            byte[] arbitratorPubKey
            */

            processModel.setPayoutTxSignature(payoutTxSignature);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

