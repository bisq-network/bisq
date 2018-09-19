package bisq.httpapi.facade;

import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;

public class FacadeUtil {

    @NotNull
    public static <T> CompletableFuture<T> failFuture(CompletableFuture<T> futureResult, Throwable throwable) {
        futureResult.completeExceptionally(throwable);
        return futureResult;
    }
}
