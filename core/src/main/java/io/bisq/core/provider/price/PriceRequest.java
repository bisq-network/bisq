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
