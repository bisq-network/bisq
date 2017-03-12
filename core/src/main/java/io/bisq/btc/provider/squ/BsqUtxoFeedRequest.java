package io.bisq.btc.provider.squ;

import com.google.common.util.concurrent.*;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class BsqUtxoFeedRequest {
    private static final Logger log = LoggerFactory.getLogger(BsqUtxoFeedRequest.class);

    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService("FeeRequest", 3, 5, 10 * 60);

    public BsqUtxoFeedRequest() {
    }

    public SettableFuture<Tuple2<Map<String, Long>, BsqUtxoFeedData>> getFees(BsqUtxoFeedProvider provider) {
        final SettableFuture<Tuple2<Map<String, Long>, BsqUtxoFeedData>> resultFuture = SettableFuture.create();
        ListenableFuture<Tuple2<Map<String, Long>, BsqUtxoFeedData>> future = executorService.submit(() -> {
            Thread.currentThread().setName("BsqUtxoRequest-" + provider.toString());
            return provider.getBsqUtxo();
        });

        Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, BsqUtxoFeedData>>() {
            public void onSuccess(Tuple2<Map<String, Long>, BsqUtxoFeedData> squUtxoData) {
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
