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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import java.util.Arrays;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerSignAndFinalizePayoutTx extends TradeTask {

    public SellerSignAndFinalizePayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkNotNull(trade.getAmount(), "trade.getTradeAmount() must not be null");

            Offer offer = trade.getOffer();
            TradingPeer tradingPeer = processModel.getTradePeer();
            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            final byte[] buyerSignature = tradingPeer.getSignature();

            Coin buyerPayoutAmount = checkNotNull(offer.getBuyerSecurityDeposit()).add(trade.getAmount());
            Coin sellerPayoutAmount = offer.getSellerSecurityDeposit();

            final String buyerPayoutAddressString = tradingPeer.getPayoutAddressString();
            String sellerPayoutAddressString = walletService.getOrCreateAddressEntry(id,
                    AddressEntry.Context.TRADE_PAYOUT).getAddressString();

            final byte[] buyerMultiSigPubKey = tradingPeer.getMultiSigPubKey();
            byte[] sellerMultiSigPubKey = processModel.getMyMultiSigPubKey();

            Optional<AddressEntry> multiSigAddressEntryOptional = walletService.getAddressEntry(id,
                    AddressEntry.Context.MULTI_SIG);
            if (!multiSigAddressEntryOptional.isPresent() || !Arrays.equals(sellerMultiSigPubKey,
                    multiSigAddressEntryOptional.get().getPubKey())) {
                // In some error edge cases it can be that the address entry is not marked (or was unmarked).
                // We do not want to fail in that case and only report a warning.
                // One case where that helped to avoid a failed payout attempt was when the taker had a power failure
                // at the moment when the offer was taken. This caused first to not see step 1 in the trade process
                // (all greyed out) but after the deposit tx was confirmed the trade process was on step 2 and
                // everything looked ok. At the payout multiSigAddressEntryOptional was not present and payout
                // could not be done. By changing the previous behaviour from fail if multiSigAddressEntryOptional
                // is not present to only log a warning the payout worked.
                log.warn("sellerMultiSigPubKey from AddressEntry does not match the one from the trade data. " +
                        "Trade id ={}, multiSigAddressEntryOptional={}", id, multiSigAddressEntryOptional);
            }

            DeterministicKey multiSigKeyPair = walletService.getMultiSigKeyPair(id, sellerMultiSigPubKey);

            Transaction transaction = processModel.getTradeWalletService().sellerSignsAndFinalizesPayoutTx(
                    checkNotNull(trade.getDepositTx()),
                    buyerSignature,
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString,
                    multiSigKeyPair,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey
            );

            trade.setPayoutTx(transaction);

            processModel.getTradeManager().requestPersistence();

            walletService.resetCoinLockedInMultiSigAddressEntry(id);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
