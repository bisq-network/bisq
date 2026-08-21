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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CompletableFutureUtils {
    /**
     * @param list List of futures
     * @param <T>  The generic type of the future
     * @return Returns a CompletableFuture with a list of the futures we got as parameter once all futures
     * are completed (incl. exceptionally completed).
     */
    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> list) {
        CompletableFuture<Void> allFuturesResult = CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()]));
        return allFuturesResult.thenApply(v ->
                list.stream().
                        map(CompletableFuture::join).
                        collect(Collectors.<T>toList())
        );
    }
}
