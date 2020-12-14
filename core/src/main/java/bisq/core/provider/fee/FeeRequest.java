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

package bisq.core.provider.fee;

import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

public class FeeRequest {
    private static final Logger log = LoggerFactory.getLogger(FeeRequest.class);

    private static final ListeningExecutorService executorService = Utilities.getListeningExecutorService("FeeRequest", 3, 5, 10 * 60);

    public FeeRequest() {
    }

    public SettableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> getFees(FeeProvider provider) {
        final SettableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> resultFuture = SettableFuture.create();
        ListenableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> future = executorService.submit(() -> {
            Thread.currentThread().setName("FeeRequest @ " + provider.getHttpClient().getBaseUrl());
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
        }, MoreExecutors.directExecutor());

        return resultFuture;
    }
}
