package io.bitsquare.btc.provider.squ;

import com.google.common.util.concurrent.*;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Utilities;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SquUtxoFeedRequest {
    private static final Logger log = LoggerFactory.getLogger(SquUtxoFeedRequest.class);

    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService("FeeRequest", 3, 5, 10 * 60);

    public SquUtxoFeedRequest() {
    }

    public SettableFuture<Tuple2<Map<String, Long>, SquUtxoFeedData>> getFees(SquUtxoFeedProvider provider) {
        final SettableFuture<Tuple2<Map<String, Long>, SquUtxoFeedData>> resultFuture = SettableFuture.create();
        ListenableFuture<Tuple2<Map<String, Long>, SquUtxoFeedData>> future = executorService.submit(() -> {
            Thread.currentThread().setName("SquUtxoRequest-" + provider.toString());
            return provider.getSquUtxo();
        });

        Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, SquUtxoFeedData>>() {
            public void onSuccess(Tuple2<Map<String, Long>, SquUtxoFeedData> squUtxoData) {
                log.debug("Received squUtxo of {}\nfrom provider {}", squUtxoData, provider);
                resultFuture.set(squUtxoData);
            }

            public void onFailure(@NotNull Throwable throwable) {
                resultFuture.setException(throwable);
            }
        });

        return resultFuture;
    }
}
