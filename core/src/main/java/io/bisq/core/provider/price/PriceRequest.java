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
package io.bisq.core.provider.price;

import com.google.common.util.concurrent.*;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Slf4j
public class PriceRequest {
    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService("PriceRequest", 3, 5, 10 * 60);

    public PriceRequest() {
    }

    public SettableFuture<Tuple2<Map<String, Long>, Map<String, MarketPrice>>> requestAllPrices(PriceProvider provider) {
        final SettableFuture<Tuple2<Map<String, Long>, Map<String, MarketPrice>>> resultFuture = SettableFuture.create();
        ListenableFuture<Tuple2<Map<String, Long>, Map<String, MarketPrice>>> future = executorService.submit(() -> {
            Thread.currentThread().setName("PriceRequest-" + provider.toString());
            return provider.getAll();
        });

        Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, Map<String, MarketPrice>>>() {
            public void onSuccess(Tuple2<Map<String, Long>, Map<String, MarketPrice>> marketPriceTuple) {
                log.debug("Received marketPriceTuple of {}\nfrom provider {}", marketPriceTuple, provider);
                resultFuture.set(marketPriceTuple);
            }

            public void onFailure(@NotNull Throwable throwable) {
                resultFuture.setException(throwable);
            }
        });

        return resultFuture;
    }
}
