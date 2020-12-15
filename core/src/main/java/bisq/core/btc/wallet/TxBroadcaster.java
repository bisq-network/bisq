/*
 * This file is part of Bisq.
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

package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.http.MemPoolSpaceTxBroadcaster;

import bisq.common.Timer;
import bisq.common.UserThread;

import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@Slf4j
public class TxBroadcaster {
    public interface Callback {
        void onSuccess(Transaction transaction);

        default void onTimeout(TxBroadcastTimeoutException exception) {
            Transaction tx = exception.getLocalTx();
            if (tx != null) {
                // We optimistically assume that the tx broadcast succeeds later and call onSuccess on the callback handler.
                // This behaviour carries less potential problems than if we would trigger a failure (e.g. which would cause
                // a failed create offer attempt or failed take offer attempt).
                // We have no guarantee how long it will take to get the information that sufficiently many BTC nodes have
                // reported back to BitcoinJ that the tx is in their mempool.
                // In normal situations that's very fast but in some cases it can take minutes (mostly related to Tor
                // connection issues). So if we just go on in the application logic and treat it as successful and the
                // tx will be broadcast successfully later all is fine.
                // If it will fail to get broadcast, it will lead to a failure state, the same as if we would trigger a
                // failure due the timeout.
                // So we can assume that this behaviour will lead to less problems as otherwise.
                // Long term we should implement better monitoring for Tor and the provided Bitcoin nodes to find out
                // why those delays happen and add some rollback behaviour to the app state in case the tx will never
                // get broadcast.
                log.warn("TxBroadcaster.onTimeout called: {}", exception.toString());
                onSuccess(tx);
            } else {
                log.error("TxBroadcaster.onTimeout: Tx is null. exception={} ", exception.toString());
                onFailure(exception);
            }
        }

        void onFailure(TxBroadcastException exception);
    }

    // Currently there is a bug in BitcoinJ causing the timeout at all BSQ transactions.
    // It is because BitcoinJ does not handle confidence object correctly in case as tx got altered after the
    // Wallet.complete() method is called which is the case for all BSQ txs. We will work on a fix for that but that
    // will take more time. In the meantime we reduce the timeout to 5 seconds to avoid that the trade protocol runs
    // into a timeout when using BSQ for trade fee.
    // For trade fee txs we set only 1 sec timeout for now.
    // FIXME
    private static final int DEFAULT_BROADCAST_TIMEOUT = 5;
    private static final Map<String, Timer> broadcastTimerMap = new HashMap<>();

    public static void broadcastTx(Wallet wallet, PeerGroup peerGroup, Transaction localTx, Callback callback) {
        broadcastTx(wallet, peerGroup, localTx, callback, DEFAULT_BROADCAST_TIMEOUT);
    }

    public static void broadcastTx(Wallet wallet, PeerGroup peerGroup, Transaction tx, Callback callback, int timeOut) {
        Timer timeoutTimer;
        final String txId = tx.getTxId().toString();
        log.info("Txid: {} hex: {}", txId, Utils.HEX.encode(tx.bitcoinSerialize()));
        if (!broadcastTimerMap.containsKey(txId)) {
            timeoutTimer = UserThread.runAfter(() -> {
                log.warn("Broadcast of tx {} not completed after {} sec.", txId, timeOut);
                stopAndRemoveTimer(txId);
                UserThread.execute(() -> callback.onTimeout(new TxBroadcastTimeoutException(tx, timeOut, wallet)));
            }, timeOut);

            broadcastTimerMap.put(txId, timeoutTimer);
        } else {
            // Would be the wrong way how to use the API (calling 2 times a broadcast with same tx).
            // An arbitrator reported that got the error after a manual payout, need to investigate why...
            stopAndRemoveTimer(txId);
            UserThread.execute(() -> callback.onFailure(new TxBroadcastException("We got broadcastTx called with a tx " +
                    "which has an open timeoutTimer. txId=" + txId, txId)));
        }

        // We decided the least risky scenario is to commit the tx to the wallet and broadcast it later.
        // If it's a bsq tx WalletManager.publishAndCommitBsqTx() should have committed the tx to both bsq and btc
        // wallets so the next line causes no effect.
        // If it's a btc tx, the next line adds the tx to the wallet.
        wallet.maybeCommitTx(tx);

        Futures.addCallback(peerGroup.broadcastTransaction(tx).future(), new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                // We expect that there is still a timeout in our map, otherwise the timeout got triggered
                if (broadcastTimerMap.containsKey(txId)) {
                    stopAndRemoveTimer(txId);
                    // At regtest we get called immediately back but we want to make sure that the handler is not called
                    // before the caller is finished.
                    UserThread.execute(() -> callback.onSuccess(tx));
                } else {
                    log.warn("We got an onSuccess callback for a broadcast which already triggered the timeout. txId={}", txId);
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                stopAndRemoveTimer(txId);
                UserThread.execute(() -> callback.onFailure(new TxBroadcastException("We got an onFailure from " +
                        "the peerGroup.broadcastTransaction callback.", throwable)));
            }
        }, MoreExecutors.directExecutor());

        // For better redundancy in case the broadcast via BitcoinJ fails we also
        // publish the tx via mempool nodes.
        MemPoolSpaceTxBroadcaster.broadcastTx(tx);
    }

    private static void stopAndRemoveTimer(String txId) {
        Timer timer = broadcastTimerMap.get(txId);
        if (timer != null)
            timer.stop();

        broadcastTimerMap.remove(txId);
    }
}
