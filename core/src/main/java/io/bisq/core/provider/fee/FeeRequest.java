package io.bisq.core.provider.fee;

import com.google.common.util.concurrent.*;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FeeRequest {
    private static final Logger log = LoggerFactory.getLogger(FeeRequest.class);

    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService("FeeRequest", 3, 5, 10 * 60);

    public FeeRequest() {
    }

    public SettableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> getFees(FeeProvider provider) {
        final SettableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> resultFuture = SettableFuture.create();
        ListenableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> future = executorService.submit(() -> {
            Thread.currentThread().setName("FeeRequest-" + provider.toString());
            return provider.getFees();
        });

        Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, Map<String, Long>>>() {
            public void onSuccess(Tuple2<Map<String, Long>, Map<String, Long>> feeData) {
                log.debug("Received feeData of {}\nfrom provider {}", feeData, provider);
                resultFuture.set(feeData);
            }

            public void onFailure(@NotNull Throwable throwable) {
                resultFuture.setException(throwable);
            }
        });

        return resultFuture;
    }
}
