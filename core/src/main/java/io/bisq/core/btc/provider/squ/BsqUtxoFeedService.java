/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.btc.provider.squ;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.handlers.FaultHandler;
import io.bisq.common.util.Tuple2;
import io.bisq.core.provider.ProvidersRepository;
import io.bisq.network.http.HttpClient;
import org.bitcoinj.core.UTXO;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public class BsqUtxoFeedService {
    private static final Logger log = LoggerFactory.getLogger(BsqUtxoFeedService.class);

    private final BsqUtxoFeedProvider bsqUtxoFeedProvider;
    private BsqUtxoFeedData data;
    private Map<String, Long> timeStampMap;
    private long epochInSecondAtLastRequest;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqUtxoFeedService(HttpClient httpClient,
                              ProvidersRepository providersRepository) {
        this.bsqUtxoFeedProvider = new BsqUtxoFeedProvider(httpClient, providersRepository.getBaseUrl());
    }

    public void onAllServicesInitialized() {
        requestBsqUtxo(null, null);
    }

    public void requestBsqUtxo(@Nullable Consumer<Set<UTXO>> resultHandler, @Nullable FaultHandler faultHandler) {
        //TODO add throttle
        Log.traceCall();
        BsqUtxoFeedRequest squUtxoRequest = new BsqUtxoFeedRequest();
        SettableFuture<Tuple2<Map<String, Long>, BsqUtxoFeedData>> future = squUtxoRequest.getFees(bsqUtxoFeedProvider);
        Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, BsqUtxoFeedData>>() {
            @Override
            public void onSuccess(@Nullable Tuple2<Map<String, Long>, BsqUtxoFeedData> result) {
                UserThread.execute(() -> {
                    checkNotNull(result, "Result must not be null at getFees");
                    timeStampMap = result.first;
                    epochInSecondAtLastRequest = timeStampMap.get("getBsqUtxoTs");
                    data = result.second;
                    if (resultHandler != null)
                        resultHandler.accept(data.getUtxoSet());
                });
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.warn("Could not load fees. " + throwable.toString());
                if (faultHandler != null)
                    UserThread.execute(() -> faultHandler.handleFault("Could not load fees", throwable));
            }
        });
    }

    public Set<UTXO> getUtxoSet() {
        return data != null ? data.getUtxoSet() : null;
    }
}
