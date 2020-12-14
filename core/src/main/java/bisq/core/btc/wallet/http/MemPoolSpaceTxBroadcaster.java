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

package bisq.core.btc.wallet.http;

import bisq.core.user.Preferences;

import bisq.network.Socks5ProxyProvider;
import bisq.network.http.HttpException;

import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MemPoolSpaceTxBroadcaster {
    private static Socks5ProxyProvider socks5ProxyProvider;
    private static Preferences preferences;
    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService(
            "MemPoolSpaceTxBroadcaster", 3, 5, 10 * 60);

    public static void init(Socks5ProxyProvider socks5ProxyProvider,
                            Preferences preferences) {
        MemPoolSpaceTxBroadcaster.socks5ProxyProvider = socks5ProxyProvider;
        MemPoolSpaceTxBroadcaster.preferences = preferences;
    }

    public static void broadcastTx(Transaction tx) {
        if (!Config.baseCurrencyNetwork().isMainnet()) {
            log.info("MemPoolSpaceTxBroadcaster only supports mainnet");
            return;
        }

        if (socks5ProxyProvider == null) {
            log.warn("We got broadcastTx called before init was called.");
            return;
        }

        String txIdToSend = tx.getTxId().toString();
        String rawTx = Utils.HEX.encode(tx.bitcoinSerialize(true));

        List<String> txBroadcastServices = new ArrayList<>(preferences.getDefaultTxBroadcastServices());
        // Broadcast to first service
        String serviceAddress = broadcastTx(txIdToSend, rawTx, txBroadcastServices);
        if (serviceAddress != null) {
            // Broadcast to second service
            txBroadcastServices.remove(serviceAddress);
            broadcastTx(txIdToSend, rawTx, txBroadcastServices);
        }
    }

    @Nullable
    private static String broadcastTx(String txIdToSend, String rawTx, List<String> txBroadcastServices) {
        String serviceAddress = getRandomServiceAddress(txBroadcastServices);
        if (serviceAddress == null) {
            log.warn("We don't have a serviceAddress available. txBroadcastServices={}", txBroadcastServices);
            return null;
        }
        broadcastTx(serviceAddress, txIdToSend, rawTx);
        return serviceAddress;
    }

    private static void broadcastTx(String serviceAddress, String txIdToSend, String rawTx) {
        TxBroadcastHttpClient httpClient = new TxBroadcastHttpClient(socks5ProxyProvider);
        httpClient.setBaseUrl(serviceAddress);
        httpClient.setIgnoreSocks5Proxy(false);

        log.info("We broadcast rawTx {} to {}", rawTx, serviceAddress);
        ListenableFuture<String> future = executorService.submit(() -> {
            Thread.currentThread().setName("MemPoolSpaceTxBroadcaster @ " + serviceAddress);
            return httpClient.post(rawTx, "User-Agent", "bisq/" + Version.VERSION);
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(String txId) {
                if (txId.equals(txIdToSend)) {
                    log.info("Broadcast of raw tx with txId {} to {} was successful. rawTx={}",
                            txId, serviceAddress, rawTx);
                } else {
                    log.error("The txId we got returned from the service does not match " +
                                    "out tx of the sending tx. txId={}; txIdToSend={}",
                            txId, txIdToSend);
                }
            }

            public void onFailure(@NotNull Throwable throwable) {
                Throwable cause = throwable.getCause();
                if (cause instanceof HttpException) {
                    int responseCode = ((HttpException) cause).getResponseCode();
                    String message = cause.getMessage();
                    // See all error codes at: https://github.com/bitcoin/bitcoin/blob/master/src/rpc/protocol.h
                    if (responseCode == 400 && message.contains("code\":-27")) {
                        log.info("Broadcast of raw tx to {} failed as transaction {} is already confirmed",
                                serviceAddress, txIdToSend);
                    } else {
                        log.info("Broadcast of raw tx to {} failed for transaction {}. responseCode={}, error={}",
                                serviceAddress, txIdToSend, responseCode, message);
                    }
                } else {
                    log.warn("Broadcast of raw tx with txId {} to {} failed. Error={}",
                            txIdToSend, serviceAddress, throwable.toString());
                }
            }
        }, MoreExecutors.directExecutor());
    }

    @Nullable
    private static String getRandomServiceAddress(List<String> txBroadcastServices) {
        List<String> list = checkNotNull(txBroadcastServices);
        return !list.isEmpty() ? list.get(new Random().nextInt(list.size())) : null;
    }
}
