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

package bisq.core.provider.mempool;

import bisq.core.provider.MempoolHttpClient;
import bisq.core.user.Preferences;

import bisq.network.Socks5ProxyProvider;

import bisq.common.util.Utilities;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MempoolRequest {
    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService("MempoolRequest", 3, 5, 10 * 60);
    private final List<String> txBroadcastServices = new ArrayList<>();
    private final MempoolHttpClient mempoolHttpClient;

    public MempoolRequest(Preferences preferences, Socks5ProxyProvider socks5ProxyProvider) {
        this.txBroadcastServices.addAll(preferences.getDefaultTxBroadcastServices());
        this.mempoolHttpClient = new MempoolHttpClient(socks5ProxyProvider);
    }

    public void getTxStatus(SettableFuture<String> mempoolServiceCallback, String txId) {
        mempoolHttpClient.setBaseUrl(getRandomServiceAddress(txBroadcastServices));
        ListenableFuture<String> future = executorService.submit(() -> {
            Thread.currentThread().setName("MempoolRequest @ " + mempoolHttpClient.getBaseUrl());
            log.info("Making http request for information on txId: {}", txId);
            return mempoolHttpClient.getTxDetails(txId);
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(String mempoolData) {
                log.info("Received mempoolData of [{}] from provider", mempoolData);
                mempoolServiceCallback.set(mempoolData);
            }
            public void onFailure(@NotNull Throwable throwable) {
                mempoolServiceCallback.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    public boolean switchToAnotherProvider() {
        txBroadcastServices.remove(mempoolHttpClient.getBaseUrl());
        return txBroadcastServices.size() > 0;
    }

    @Nullable
    private static String getRandomServiceAddress(List<String> txBroadcastServices) {
        List<String> list = checkNotNull(txBroadcastServices);
        return !list.isEmpty() ? list.get(new Random().nextInt(list.size())) : null;
    }
}

