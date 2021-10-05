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
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Transaction;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerFinalizesDelayedPayoutTx extends TradeTask {
    public SellerFinalizesDelayedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Transaction preparedDelayedPayoutTx = checkNotNull(processModel.getPreparedDelayedPayoutTx());
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            byte[] buyerMultiSigPubKey = processModel.getTradePeer().getMultiSigPubKey();
            byte[] sellerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(sellerMultiSigPubKey,
                    btcWalletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "sellerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            byte[] buyerSignature = processModel.getTradePeer().getDelayedPayoutTxSignature();
            byte[] sellerSignature = processModel.getDelayedPayoutTxSignature();

            Transaction signedDelayedPayoutTx = processModel.getTradeWalletService().finalizeDelayedPayoutTx(
                    preparedDelayedPayoutTx,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey,
                    buyerSignature,
                    sellerSignature);

            trade.applyDelayedPayoutTx(signedDelayedPayoutTx);
            log.info("DelayedPayoutTxBytes = {}", Utilities.bytesAsHexString(trade.getDelayedPayoutTxBytes()));

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
