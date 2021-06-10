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

package bisq.core.trade.atomic.protocol.tasks.taker;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicTakerPublishAtomicTx extends AtomicTradeTask {

    @SuppressWarnings({"unused"})
    public AtomicTakerPublishAtomicTx(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            // Publish atomicTx
            var atomicTx = atomicProcessModel.getVerifiedAtomicTx();

            checkNotNull(atomicTx, "Verified atomictx must not be null");

            log.info("AtomicTxBytes: {}", Utilities.bytesAsHexString(atomicTx.bitcoinSerialize()));
            atomicProcessModel.getWalletsManager().publishAndCommitBsqTx(atomicTx, TxType.TRANSFER_BSQ,
                    new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            if (!completed) {
                                atomicTrade.setState(AtomicTrade.State.TX_PUBLISHED);

                                atomicProcessModel.getBtcWalletService().swapTradeEntryToAvailableEntry(
                                        atomicProcessModel.getOffer().getId(), AddressEntry.Context.RESERVED_FOR_TRADE);

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
                    });

        } catch (Throwable t) {
            failed(t);
        }
    }
}
