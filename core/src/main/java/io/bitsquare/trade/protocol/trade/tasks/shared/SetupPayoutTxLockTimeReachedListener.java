/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.tasks.shared;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeTask;
import io.bitsquare.trade.states.BuyerTradeState;
import io.bitsquare.trade.states.SellerTradeState;

import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Threading;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupPayoutTxLockTimeReachedListener extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SetupPayoutTxLockTimeReachedListener.class);

    public SetupPayoutTxLockTimeReachedListener(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("ChainHeight/LockTime: {} / {}", processModel.getTradeWalletService().getBestChainHeight(), trade.getLockTime());
            if (processModel.getTradeWalletService().getBestChainHeight() >= trade.getLockTime()) {
                broadcastTx();
            }
            else {
                ListenableFuture<StoredBlock> blockHeightFuture = processModel.getTradeWalletService().getBlockHeightFuture(trade.getPayoutTx());
                blockHeightFuture.addListener(
                        () -> {
                            try {
                                log.debug("Block height reached " + blockHeightFuture.get().getHeight());
                            } catch (InterruptedException | ExecutionException e) {
                                e.printStackTrace();
                            }
                            broadcastTx();
                        },
                        Threading.USER_THREAD::execute);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }

    protected void broadcastTx() {
        processModel.getTradeWalletService().broadcastTx(trade.getPayoutTx(), new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.debug("BroadcastTx succeeded. Transaction:" + transaction);

                if (trade instanceof BuyerTrade)
                    trade.setProcessState(BuyerTradeState.ProcessState.PAYOUT_BROAD_CASTED);
                else if (trade instanceof SellerTrade)
                    trade.setProcessState(SellerTradeState.ProcessState.PAYOUT_BROAD_CASTED);


                complete();
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                t.printStackTrace();
                trade.setThrowable(t);
/*
                if (trade instanceof TakerTrade)
                    trade.setProcessState(TakerTradeState.ProcessState.PAYOUT_BROAD_CASTED_FAILED);
                else if (trade instanceof OffererTrade)
                    trade.setProcessState(OffererTradeState.ProcessState.PAYOUT_BROAD_CASTED_FAILED);*/


                failed(t);
            }
        });
    }
}
