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

package io.bisq.core.btc.wallet;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class Broadcaster {
    private static final int DEFAULT_BROADCAST_TIMEOUT = 8;
    private static Set<String> broadcastTimerSet = new HashSet<>();

    public static void broadcastTx(Wallet wallet, PeerGroup peerGroup, Transaction tx, FutureCallback<Transaction> callback) {
        broadcastTx(wallet, peerGroup, tx, callback, DEFAULT_BROADCAST_TIMEOUT);
    }

    public static void broadcastTx(Wallet wallet, PeerGroup peerGroup, Transaction tx, FutureCallback<Transaction> callback, int timeoutInSec) {
        final Timer timeoutTimer;
        if (!broadcastTimerSet.contains(tx.getHashAsString())) {
            timeoutTimer = UserThread.runAfter(() -> {
                log.warn("Broadcast of tx {} not completed after {} sec. We optimistically assume that the tx broadcast succeeded and " +
                        "call onSuccess on the callback handler.", tx.getHashAsString(), timeoutInSec);

                wallet.maybeCommitTx(tx);

                callback.onSuccess(tx);

                broadcastTimerSet.remove(tx.getHashAsString());
            }, timeoutInSec);
            broadcastTimerSet.add(tx.getHashAsString());
        } else {
            timeoutTimer = null;
        }

        Futures.addCallback(peerGroup.broadcastTransaction(tx).future(), new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                // At regtest we get called immediately back but we want to make sure that the handler is not called
                // before the caller is finished.
                UserThread.execute(() -> {
                    if (broadcastTimerSet.contains(tx.getHashAsString())) {
                        // If the timeout has not been called we call the callback.onSuccess
                        stopAndRemoveTimer(timeoutTimer, tx);

                        if (result != null)
                            wallet.maybeCommitTx(result);

                        callback.onSuccess(tx);
                    } else {
                        // Timeout was triggered, nothing to do anymore.
                        log.info("onSuccess for tx {} was already called from timeout handler. ", tx.getHashAsString());
                    }
                });
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                UserThread.execute(() -> {
                    stopAndRemoveTimer(timeoutTimer, tx);

                    callback.onFailure(t);
                });
            }
        });
    }

    private static void stopAndRemoveTimer(@Nullable Timer timer, Transaction tx) {
        if (timer != null)
            timer.stop();

        broadcastTimerSet.remove(tx.getHashAsString());
    }
}
