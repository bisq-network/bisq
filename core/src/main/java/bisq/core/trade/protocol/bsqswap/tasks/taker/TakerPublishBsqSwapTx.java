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

package bisq.core.trade.protocol.bsqswap.tasks.taker;

import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TakerPublishBsqSwapTx extends BsqSwapTask {

    @SuppressWarnings({"unused"})
    public TakerPublishBsqSwapTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            // Publish transaction
         /*   var transaction = bsqSwapProtocolModel.getVerifiedTransaction();

            checkNotNull(transaction, "Verified transaction must not be null");

            log.debug("Transaction bytes: {}", Utilities.bytesAsHexString(transaction.bitcoinSerialize()));
            bsqSwapProtocolModel.getWalletsManager().publishAndCommitBsqTx(transaction, TxType.TRANSFER_BSQ,
                    new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            if (!completed) {
                                bsqSwapTrade.setState(BsqSwapTrade.State.TX_PUBLISHED);

                                bsqSwapProtocolModel.getBtcWalletService().swapTradeEntryToAvailableEntry(
                                        bsqSwapProtocolModel.getOffer().getId(), AddressEntry.Context.RESERVED_FOR_TRADE);

                                complete();
                            } else {
                                log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                            }
                        }

                        @Override
                        public void onFailure(TxBroadcastException exception) {
                            if (!completed) {
                                failed(exception);
                            } else {
                                log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                            }
                        }
                    });*/

        } catch (Throwable t) {
            failed(t);
        }
    }
}
