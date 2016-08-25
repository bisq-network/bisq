package io.bitsquare.btc.pricefeed;

import com.google.common.util.concurrent.*;
import io.bitsquare.btc.pricefeed.providers.PriceProvider;
import io.bitsquare.common.util.Utilities;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

class GetPriceRequest {
    private static final Logger log = LoggerFactory.getLogger(GetPriceRequest.class);

    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService("GetPriceRequest", 3, 5, 10 * 60);

    public GetPriceRequest() {
    }

    public SettableFuture<Map<String, MarketPrice>> requestAllPrices(PriceProvider provider) {
        final SettableFuture<Map<String, MarketPrice>> resultFuture = SettableFuture.create();
        ListenableFuture<Map<String, MarketPrice>> future = executorService.submit(() -> {
            Thread.currentThread().setName("requestAllPrices-" + provider.toString());
            return provider.getAllPrices();
        });

        Futures.addCallback(future, new FutureCallback<Map<String, MarketPrice>>() {
            public void onSuccess(Map<String, MarketPrice> marketPrice) {
                log.debug("Received marketPrice of {}\nfrom provider {}", marketPrice, provider);
                resultFuture.set(marketPrice);
            }

            public void onFailure(@NotNull Throwable throwable) {
                resultFuture.setException(throwable);
            }
        });

        return resultFuture;
    }

    public SettableFuture<MarketPrice> requestPrice(String currencyCode, PriceProvider provider) {
        final SettableFuture<MarketPrice> resultFuture = SettableFuture.create();
        return requestPrice(currencyCode, provider, resultFuture);
    }

    private SettableFuture<MarketPrice> requestPrice(String currencyCode, PriceProvider provider, SettableFuture<MarketPrice> resultFuture) {
        // Log.traceCall(currencyCode);
        ListenableFuture<MarketPrice> future = executorService.submit(() -> {
            Thread.currentThread().setName("requestPrice-" + provider.toString());
            return provider.getPrice(currencyCode);
        });

        Futures.addCallback(future, new FutureCallback<MarketPrice>() {
            public void onSuccess(MarketPrice marketPrice) {
                log.debug("Received marketPrice of {}\nfor currencyCode {}\nfrom provider {}",
                        marketPrice, currencyCode, provider);
                resultFuture.set(marketPrice);
            }

            public void onFailure(@NotNull Throwable throwable) {
                log.debug("requestPrice.onFailure: throwable=" + throwable.toString());
                resultFuture.setException(throwable);
            }
        });

        return resultFuture;
    }
}
