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

package bisq.core.provider.price;

import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class PriceRequest {
    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService("PriceRequest", 3, 5, 10 * 60);
    @Nullable
    private PriceProvider provider;
    private boolean shutDownRequested;

    public PriceRequest() {
    }

    public SettableFuture<Tuple2<Map<String, Long>, Map<String, MarketPrice>>> requestAllPrices(PriceProvider provider) {
        this.provider = provider;
        String baseUrl = provider.getBaseUrl();
        SettableFuture<Tuple2<Map<String, Long>, Map<String, MarketPrice>>> resultFuture = SettableFuture.create();
        ListenableFuture<Tuple2<Map<String, Long>, Map<String, MarketPrice>>> future = executorService.submit(() -> {
            Thread.currentThread().setName("PriceRequest @ " + baseUrl);
            return provider.getAll();
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(Tuple2<Map<String, Long>, Map<String, MarketPrice>> marketPriceTuple) {
                log.trace("Received marketPriceTuple of {}\nfrom provider {}", marketPriceTuple, provider);
                if (!shutDownRequested) {
                    resultFuture.set(marketPriceTuple);
                }

            }

            public void onFailure(@NotNull Throwable throwable) {
                if (!shutDownRequested) {
                    resultFuture.setException(new PriceRequestException(throwable, baseUrl));
                }
            }
        }, MoreExecutors.directExecutor());

        return resultFuture;
    }

    public void shutDown() {
        shutDownRequested = true;
        if (provider != null) {
            provider.shutDown();
        }
        Utilities.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS);
    }
}
