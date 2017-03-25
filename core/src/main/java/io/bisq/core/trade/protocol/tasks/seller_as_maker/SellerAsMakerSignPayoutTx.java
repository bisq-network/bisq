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

package io.bisq.core.trade.protocol.tasks.seller_as_maker;

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
public class SellerAsMakerSignPayoutTx extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SellerAsMakerSignPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Preconditions.checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            Preconditions.checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
            Coin sellerPayoutAmount = trade.getOffer().getSellerSecurityDeposit();
            Coin buyerPayoutAmount = trade.getOffer().getBuyerSecurityDeposit()
                    .add(trade.getTradeAmount());


            //TODO: locktime  
            // We use the sellers LastBlockSeenHeight, which might be different to the buyers one.
            // If lock time is 0 we set lockTimeAsBlockHeight to 0 to mark it as "not set". 
            // In the tradeWallet we apply the lockTime only if it is set, otherwise we use the default values for 
            // transaction lockTime and sequence number
           /* long lockTime = trade.getOffer().getPaymentMethod().getLockTime();
            long lockTimeAsBlockHeight = 0;
            if (lockTime > 0)
                lockTimeAsBlockHeight = processModel.getTradeWalletService().getLastBlockSeenHeight() + lockTime;
            trade.setLockTimeAsBlockHeight(lockTimeAsBlockHeight);*/

            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            String sellerPayoutAddressString = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT).getAddressString();
            DeterministicKey multiSigKeyPair = walletService.getMultiSigKeyPair(id, processModel.getMyMultiSigPubKey());
            byte[] sellerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(sellerMultiSigPubKey,
                            walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "sellerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            byte[] payoutTxSignature = processModel.getTradeWalletService().sellerSignsPayoutTx(
                    trade.getDepositTx(),
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    processModel.tradingPeer.getPayoutAddressString(),
                    sellerPayoutAddressString,
                    multiSigKeyPair,
                 /*   lockTimeAsBlockHeight,*/
                    processModel.tradingPeer.getMultiSigPubKey(),
                    sellerMultiSigPubKey,
                    trade.getArbitratorPubKey());

            processModel.setPayoutTxSignature(payoutTxSignature);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

