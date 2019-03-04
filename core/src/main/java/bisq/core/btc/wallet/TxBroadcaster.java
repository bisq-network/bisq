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
import bisq.core.btc.exceptions.TxMalleabilityException;

import bisq.common.Timer;
import bisq.common.UserThread;

import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

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
                log.warn("TxBroadcaster.onTimeout called: {} \n" +
                                "We optimistically assume that the tx broadcast succeeds later and call onSuccess on the " +
                                "callback handler. This behaviour carries less potential problems than if we would trigger " +
                                "a failure (e.g. which would cause a failed create offer attempt of failed take offer attempt).\n" +
                                "We have no guarantee how long it will take to get the information that sufficiently many BTC " +
                                "nodes have reported back to BitcoinJ that the tx is in their mempool.\n" +
                                "In normal situations " +
                                "that's very fast but in some cases it can take minutes (mostly related to Tor connection " +
                                "issues). So if we just go on in the application logic and treat it as successful and the " +
                                "tx will be broadcast successfully later all is fine.\n" +
                                "If it will fail to get broadcast, " +
                                "it will lead to a failure state, the same as if we would trigger a failure due the timeout." +
                                "So we can assume that this behaviour will lead to less problems as otherwise.\n" +
                                "Long term we should implement better monitoring for Tor and the provided Bitcoin nodes to " +
                                "find out why those delays happen and add some rollback behaviour to the app state in case " +
                                "the tx will never get broadcast.",
                        exception.toString());

                // The wallet.maybeCommitTx() call is required in case the tx is spent by a follow up tx as otherwise there would be an
                // InsufficientMoneyException thrown. But in some test scenarios we also got issues with wallet
                // inconsistency if the tx was committed twice. It should be prevented by the maybeCommitTx methods but
                // not 100% if that is always the case. Just added that comment to make clear that this might be a risky
                // strategy and might need improvement if we get problems.
                // UPDATE: We got reported an wallet problem that a tx was added twice and wallet could not be loaded anymore even after a SPV resync.
                // So it seems that this strategy is too risky to cause more problems as it tries to solve.
                // Need more work from a BitcoinJ expert! For now we comment the call out here but leave it as reference
                // for future improvements.
                // exception.getWallet().maybeCommitTx(tx);

                onSuccess(tx);
            } else {
                log.error("TxBroadcaster.onTimeout: Tx is null. exception={} ", exception.toString());
                onFailure(exception);
            }
        }

        default void onTxMalleability(TxMalleabilityException exception) {
            log.error("onTxMalleability.onTimeout " + exception.toString());
            onFailure(exception);
        }

        void onFailure(TxBroadcastException exception);
    }

    private static final int DEFAULT_BROADCAST_TIMEOUT = 30;
    private static Map<String, Timer> broadcastTimerMap = new HashMap<>();

    public static void broadcastTx(Wallet wallet, PeerGroup peerGroup, Transaction localTx, Callback callback) {
        broadcastTx(wallet, peerGroup, localTx, callback, DEFAULT_BROADCAST_TIMEOUT);
    }

    public static void broadcastTx(Wallet wallet, PeerGroup peerGroup, Transaction tx, Callback callback, int delayInSec) {
        Timer timeoutTimer;
        final String txId = tx.getHashAsString();
        if (!broadcastTimerMap.containsKey(txId)) {
            timeoutTimer = UserThread.runAfter(() -> {
                log.warn("Broadcast of tx {} not completed after {} sec.", txId, delayInSec);
                stopAndRemoveTimer(txId);
                UserThread.execute(() -> callback.onTimeout(new TxBroadcastTimeoutException(tx, delayInSec, wallet)));
            }, delayInSec);

            broadcastTimerMap.put(txId, timeoutTimer);
        } else {
            // Would be due a wrong way how to use the API (calling 2 times a broadcast with same tx).
            // An arbitrator reported to got the error after a manual payout, need to investigate why...
            stopAndRemoveTimer(txId);
            UserThread.execute(() -> callback.onFailure(new TxBroadcastException("We got broadcastTx called with a tx " +
                    "which has an open timeoutTimer. txId=" + txId, txId)));
        }

        Futures.addCallback(peerGroup.broadcastTransaction(tx).future(), new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                if (result != null) {
                    if (txId.equals(result.getHashAsString())) {
                        // We expect that there is still a timeout in our map, otherwise the timeout got triggered
                        if (broadcastTimerMap.containsKey(txId)) {
                            wallet.maybeCommitTx(tx);
                            stopAndRemoveTimer(txId);
                            // At regtest we get called immediately back but we want to make sure that the handler is not called
                            // before the caller is finished.
                            UserThread.execute(() -> callback.onSuccess(tx));
                        } else {
                            stopAndRemoveTimer(txId);
                            log.warn("We got an onSuccess callback for a broadcast which already triggered the timeout.", txId);
                        }
                    } else {
                        stopAndRemoveTimer(txId);
                        UserThread.execute(() -> callback.onTxMalleability(new TxMalleabilityException(tx, result)));
                    }
                } else {
                    stopAndRemoveTimer(txId);
                    UserThread.execute(() -> callback.onFailure(new TxBroadcastException("Transaction returned from the " +
                            "broadcastTransaction call back is null.", txId)));
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                stopAndRemoveTimer(txId);
                UserThread.execute(() -> callback.onFailure(new TxBroadcastException("We got an onFailure from " +
                        "the peerGroup.broadcastTransaction callback.", throwable)));
            }
        });
    }

    private static void stopAndRemoveTimer(String txId) {
        Timer timer = broadcastTimerMap.get(txId);
        if (timer != null)
            timer.stop();

        broadcastTimerMap.remove(txId);
    }
}
