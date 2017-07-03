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

package io.bisq.core.provider.fee;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.FaultHandler;
import io.bisq.common.util.Tuple2;
import io.bisq.core.app.BisqEnvironment;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
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

    // https://litecoin.info/Transaction_fees
    //0.001 (LTC)/kb -> 0.00100000 sat/kb -> 100 sat/byte
    public static final long LTC_DEFAULT_TX_FEE = 500; // min fee is 0.001 LTC 200 bytes with 500 -> 100000
    public static final long BTC_DEFAULT_TX_FEE = 200;
    public static final long DOGE_DEFAULT_TX_FEE = 500000; // min tx size is about 200 bytes -> 1 DOGE

    // Dust limit for LTC is 100 000 sat
    // https://litecoin.info/Transaction_fees
    private static long MIN_MAKER_FEE_IN_BASE_CUR;
    private static long MIN_TAKER_FEE_IN_BASE_CUR;
    private static long DEFAULT_MAKER_FEE_IN_BASE_CUR;
    private static long DEFAULT_TAKER_FEE_IN_BASE_CUR;

    private static final long MIN_MAKER_FEE_IN_MBSQ = 50; // about 0.05 EUR if 1 BSQ = 1 EUR
    private static final long MIN_TAKER_FEE_IN_MBSQ = 50;
    private static final long DEFAULT_MAKER_FEE_IN_MBSQ = 500; // about 0.5 EUR if 1 BSQ = 1 EUR
    private static final long DEFAULT_TAKER_FEE_IN_MBSQ = 750;


    // 0.00216 btc is for 3 x tx fee for taker -> about 2 EUR!

    public static final long MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN = 10;

    private final FeeProvider feeProvider;
    private final String baseCurrencyCode;
    private long txFeePerByte;
    private Map<String, Long> timeStampMap;
    private long epochInSecondAtLastRequest;
    private long lastRequest;
    private IntegerProperty feeUpdateCounter = new SimpleIntegerProperty(0);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FeeService(FeeProvider feeProvider) {
        this.feeProvider = feeProvider;
        baseCurrencyCode = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode();

        switch (baseCurrencyCode) {
            case "BTC":
                MIN_MAKER_FEE_IN_BASE_CUR = 20_000; // 0.5 USD at BTC price 2500 USD for 1 BTC
                MIN_TAKER_FEE_IN_BASE_CUR = 20_000;
                DEFAULT_MAKER_FEE_IN_BASE_CUR = 200_000; // 5 USD at BTC price 2500 USD
                DEFAULT_TAKER_FEE_IN_BASE_CUR = 300_000; // 7.5 USD at BTC price 2500 USD
                txFeePerByte = BTC_DEFAULT_TX_FEE;
                break;
            case "LTC":
                MIN_MAKER_FEE_IN_BASE_CUR = 1_200_000; // 0.5 USD at LTC price 40 USD for 50 LTC
                MIN_TAKER_FEE_IN_BASE_CUR = 1_200_000;
                DEFAULT_MAKER_FEE_IN_BASE_CUR = 240_000; // 5 USD at LTC price 40 USD
                DEFAULT_TAKER_FEE_IN_BASE_CUR = 360_000; // 7.5 USD at LTC price 40 USD
                txFeePerByte = LTC_DEFAULT_TX_FEE;
                break;
            case "DOGE":
                MIN_MAKER_FEE_IN_BASE_CUR = 32_000; // 0.5 USD at DOGE price 0.003 USD 80_000_000_000L
                MIN_TAKER_FEE_IN_BASE_CUR = 32_000;
                DEFAULT_MAKER_FEE_IN_BASE_CUR = 320_000; // 5 USD at DOGE price 0.003 USD  800_000_000_000L
                DEFAULT_TAKER_FEE_IN_BASE_CUR = 480_000; // 7.5 USD at DOGE price 0.003 USD 1_200_000_000_000L
                txFeePerByte = DOGE_DEFAULT_TX_FEE;
                break;
            default:
                throw new RuntimeException("baseCurrencyCode not defined. baseCurrencyCode=" + baseCurrencyCode);
        }
    }

    public void onAllServicesInitialized() {
        requestFees(null, null);
    }

    public void requestFees(@Nullable Runnable resultHandler, @Nullable FaultHandler faultHandler) {
        long now = Instant.now().getEpochSecond();
        if (now - lastRequest > MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN * 60) {
            lastRequest = now;
            FeeRequest feeRequest = new FeeRequest();
            SettableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> future = feeRequest.getFees(feeProvider);
            Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, Map<String, Long>>>() {
                @Override
                public void onSuccess(@Nullable Tuple2<Map<String, Long>, Map<String, Long>> result) {
                    UserThread.execute(() -> {
                        checkNotNull(result, "Result must not be null at getFees");
                        timeStampMap = result.first;
                        epochInSecondAtLastRequest = timeStampMap.get("bitcoinFeesTs");
                        final Map<String, Long> map = result.second;
                        txFeePerByte = map.get(baseCurrencyCode);
                        feeUpdateCounter.set(feeUpdateCounter.get() + 1);
                        log.info("{} tx fee: txFeePerByte={}", baseCurrencyCode, txFeePerByte);
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
        return Coin.valueOf(txFeePerByte);
    }

    public static Coin getMakerFeePerBtc(boolean currencyForMakerFeeBtc) {
        return currencyForMakerFeeBtc ? Coin.valueOf(DEFAULT_MAKER_FEE_IN_BASE_CUR) : Coin.valueOf(DEFAULT_MAKER_FEE_IN_MBSQ);
    }

    public static Coin getMinMakerFee(boolean currencyForMakerFeeBtc) {
        return currencyForMakerFeeBtc ? Coin.valueOf(MIN_MAKER_FEE_IN_BASE_CUR) : Coin.valueOf(MIN_MAKER_FEE_IN_MBSQ);
    }


    public static Coin getTakerFeePerBtc(boolean currencyForTakerFeeBtc) {
        return currencyForTakerFeeBtc ? Coin.valueOf(DEFAULT_TAKER_FEE_IN_BASE_CUR) : Coin.valueOf(DEFAULT_TAKER_FEE_IN_MBSQ);
    }

    public static Coin getMinTakerFee(boolean currencyForTakerFeeBtc) {
        return currencyForTakerFeeBtc ? Coin.valueOf(MIN_TAKER_FEE_IN_BASE_CUR) : Coin.valueOf(MIN_TAKER_FEE_IN_MBSQ);
    }


    public Coin getCreateCompensationRequestFee() {
        //TODO
        return Coin.valueOf(1000);
    }

    public Coin getVotingTxFee() {
        //TODO
        return Coin.valueOf(999);
    }

    public ReadOnlyIntegerProperty feeUpdateCounterProperty() {
        return feeUpdateCounter;
    }
}
