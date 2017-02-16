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
import java.time.Instant;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class FeeService {
    private static final Logger log = LoggerFactory.getLogger(FeeService.class);

    public static final long MIN_TX_FEE = 60; // satoshi/byte
    public static final long MAX_TX_FEE = 300;
    public static final long DEFAULT_TX_FEE = 150;

    public static final long MIN_CREATE_OFFER_FEE_IN_BTC = 10_000;
    public static final long MAX_CREATE_OFFER_FEE_IN_BTC = 500_000;
    public static final long DEFAULT_CREATE_OFFER_FEE_IN_BTC = 30_000; // excluded mining fee

    public static final long MIN_TAKE_OFFER_FEE_IN_BTC = 10_000;
    public static final long MAX_TAKE_OFFER_FEE_IN_BTC = 1000_000;
    public static final long DEFAULT_TAKE_OFFER_FEE_IN_BTC = 30_000; // excluded mining fee

    // 0.00216 btc is for 3 x tx fee for taker -> about 2 EUR!

    public static final long MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN = 10;

    private final FeeProvider feeProvider;
    @Nullable
    private FeeData feeData;
    private Map<String, Long> timeStampMap;
    private long epochInSecondAtLastRequest;
    private long lastRequest;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FeeService(HttpClient httpClient,
                      ProvidersRepository providersRepository) {
        this.feeProvider = new FeeProvider(httpClient, providersRepository.getBaseUrl());
    }

    public void onAllServicesInitialized() {
        requestFees(null, null);
    }

    public void requestFees(@Nullable Runnable resultHandler, @Nullable FaultHandler faultHandler) {
        long now = Instant.now().getEpochSecond();
        if (feeData == null || now - lastRequest > MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN * 60) {
            lastRequest = now;
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
                        log.info("Tx fee: txFeePerByte=" + feeData.txFeePerByte);
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
        } else {
            log.debug("We got a requestFees called again before min pause of {} minutes has passed.", MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN);
            UserThread.execute(() -> {
                if (resultHandler != null)
                    resultHandler.run();
            });
        }
    }

    public Coin getTxFee(int sizeInBytes) {
        return getTxFeePerByte().multiply(sizeInBytes);
    }

    public Coin getTxFeePerByte() {
        if (feeData != null)
            return Coin.valueOf(feeData.txFeePerByte);
        else
            return Coin.valueOf(DEFAULT_TX_FEE);
    }

    // TODO we will get that from the DAO voting
    public Coin getCreateOfferFeeInBtc() {
        return Coin.valueOf(DEFAULT_CREATE_OFFER_FEE_IN_BTC);
    }

    // TODO we will get that from the DAO voting
    public Coin getTakeOfferFeeInBtc() {
        return Coin.valueOf(DEFAULT_TAKE_OFFER_FEE_IN_BTC);
    }

    public Coin getCreateCompensationRequestFee() {
        //TODO
        return Coin.valueOf(1000);
    }

    public Coin getVotingTxFee() {
        //TODO
        return Coin.valueOf(999);
    }

}
