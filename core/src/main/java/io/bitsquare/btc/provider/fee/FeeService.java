/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc.provider.fee;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bitsquare.app.Log;
import io.bitsquare.btc.provider.ProvidersRepository;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.http.HttpClient;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class FeeService {
    private static final Logger log = LoggerFactory.getLogger(FeeService.class);

    public static final long MIN_TX_FEE = 40; // satoshi/byte
    public static final long MAX_TX_FEE = 200;
    public static final long DEFAULT_TX_FEE = 60;

    public static final long MIN_CREATE_OFFER_FEE = 10_000;
    public static final long MAX_CREATE_OFFER_FEE = 500_000;
    public static final long DEFAULT_CREATE_OFFER_FEE = 30_000;

    public static final long MIN_TAKE_OFFER_FEE = 10_000;
    public static final long MAX_TAKE_OFFER_FEE = 1000_000;
    public static final long DEFAULT_TAKE_OFFER_FEE = 80_000;

    private final FeeProvider feeProvider;
    private FeeData feeData;
    private Map<String, Long> timeStampMap;
    private long epochInSecondAtLastRequest;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FeeService(HttpClient httpClient,
                      ProvidersRepository providersRepository) {
        this.feeProvider = new FeeProvider(httpClient, providersRepository.getBaseUrl());
        feeData = new FeeData(DEFAULT_TX_FEE, DEFAULT_CREATE_OFFER_FEE, DEFAULT_TAKE_OFFER_FEE);
    }

    public void onAllServicesInitialized() {
        requestFees(null, null);
    }

    public void requestFees(@Nullable Runnable resultHandler, @Nullable FaultHandler faultHandler) {
        //TODO add throttle
        Log.traceCall();
        FeeRequest feeRequest = new FeeRequest();
        SettableFuture<Tuple2<Map<String, Long>, FeeData>> future = feeRequest.getFees(feeProvider);
        Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, FeeData>>() {
            @Override
            public void onSuccess(@Nullable Tuple2<Map<String, Long>, FeeData> result) {
                UserThread.execute(() -> {
                    checkNotNull(result, "Result must not be null at getFees");
                    timeStampMap = result.first;
                    epochInSecondAtLastRequest = timeStampMap.get("bitcoinFeesTs");
                    feeData = result.second;
                    if (resultHandler != null)
                        resultHandler.run();
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

    public Coin getTxFee(int sizeInBytes) {
        return getTxFeePerByte().multiply(sizeInBytes);
    }

    public Coin getTxFeePerByte() {
        log.info("txFeePerByte = " + feeData.txFeePerByte);
        return Coin.valueOf(feeData.txFeePerByte);
    }

    public Coin getCreateOfferFee() {
        return Coin.valueOf(feeData.createOfferFee);
    }

    public Coin getTakeOfferFee() {
        return Coin.valueOf(feeData.takeOfferFee);
    }

    public Coin getCreateCompensationRequestFee() {
        //TODO
        return Coin.valueOf(1000);
    }

}
