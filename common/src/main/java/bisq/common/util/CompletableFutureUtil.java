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

package bisq.common.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//todo
public class CompletableFutureUtil {
    public static <T> CompletableFuture<List<T>> allOf(Collection<CompletableFuture<T>> collection) {
        //noinspection unchecked
        return allOf(collection.toArray(new CompletableFuture[0]));
    }

    public static <T> CompletableFuture<List<T>> allOf(Stream<CompletableFuture<T>> stream) {
        return allOf(stream.collect(Collectors.toList()));
    }

    public static <T> CompletableFuture<List<T>> allOf(CompletableFuture<T>... list) {
        CompletableFuture<List<T>> result = CompletableFuture.allOf(list).thenApply(v ->
                Stream.of(list)
                        .map(future -> {
                            // We want to return the results in list, not the futures. Once allOf call is complete
                            // we know that all futures have completed (normally, exceptional or cancelled).
                            // For exceptional and canceled cases we throw an exception.
                            T res = future.join();
                            if (future.isCompletedExceptionally()) {
                                throw new RuntimeException((future.handle((r, throwable) -> throwable).join()));
                            }
                            if (future.isCancelled()) {
                                throw new RuntimeException("Future got canceled");
                            }
                            return res;
                        })
                        .collect(Collectors.<T>toList())
        );
        return result;
    }
}
