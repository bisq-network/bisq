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

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterManager;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.handlers.FaultHandler;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class FeeService {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Miner fees are between 1-600 sat/vbyte. We try to stay on the safe side. BTC_DEFAULT_TX_FEE is only used if our
    // fee service would not deliver data.
    private static final long BTC_DEFAULT_TX_FEE = 50;
    private static final long MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN = 2;
    private static DaoStateService daoStateService;
    private static PeriodService periodService;
    private static FilterManager filterManager = null;

    private static Coin getFeeFromParamAsCoin(Param param) {
        // if specified, filter values take precedence
        Coin fromFilter = getFilterFromParamAsCoin(param);
        if (fromFilter.isGreaterThan(Coin.ZERO)) {
            return fromFilter;
        }
        return daoStateService != null && periodService != null ? daoStateService.getParamValueAsCoin(param, periodService.getChainHeight()) : Coin.ZERO;
    }

    private static Coin getFilterFromParamAsCoin(Param param) {
        Coin filterVal = Coin.ZERO;
        if (filterManager != null && filterManager.getFilter() != null) {
            if (param == Param.DEFAULT_MAKER_FEE_BTC) {
                filterVal = Coin.valueOf(filterManager.getFilter().getMakerFeeBtc());
            } else if (param == Param.DEFAULT_TAKER_FEE_BTC) {
                filterVal = Coin.valueOf(filterManager.getFilter().getTakerFeeBtc());
            } else if (param == Param.DEFAULT_MAKER_FEE_BSQ) {
                filterVal = Coin.valueOf(filterManager.getFilter().getMakerFeeBsq());
            } else if (param == Param.DEFAULT_TAKER_FEE_BSQ) {
                filterVal = Coin.valueOf(filterManager.getFilter().getTakerFeeBsq());
            }
        }
        return filterVal;
    }

    public static Coin getMakerFeePerBtc(boolean currencyForFeeIsBtc) {
        return currencyForFeeIsBtc ? getFeeFromParamAsCoin(Param.DEFAULT_MAKER_FEE_BTC) : getFeeFromParamAsCoin(Param.DEFAULT_MAKER_FEE_BSQ);
    }

    public static Coin getMinMakerFee(boolean currencyForFeeIsBtc) {
        return currencyForFeeIsBtc ? getFeeFromParamAsCoin(Param.MIN_MAKER_FEE_BTC) : getFeeFromParamAsCoin(Param.MIN_MAKER_FEE_BSQ);
    }

    public static Coin getTakerFeePerBtc(boolean currencyForFeeIsBtc) {
        return currencyForFeeIsBtc ? getFeeFromParamAsCoin(Param.DEFAULT_TAKER_FEE_BTC) : getFeeFromParamAsCoin(Param.DEFAULT_TAKER_FEE_BSQ);
    }

    public static Coin getMinTakerFee(boolean currencyForFeeIsBtc) {
        return currencyForFeeIsBtc ? getFeeFromParamAsCoin(Param.MIN_TAKER_FEE_BTC) : getFeeFromParamAsCoin(Param.MIN_TAKER_FEE_BSQ);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final FeeProvider feeProvider;
    private final IntegerProperty feeUpdateCounter = new SimpleIntegerProperty(0);
    private long txFeePerVbyte = BTC_DEFAULT_TX_FEE;
    private Map<String, Long> timeStampMap;
    @Getter
    private long lastRequest;
    @Getter
    private long minFeePerVByte;
    private long epochInSecondAtLastRequest;
    private List<Tuple2<Runnable, FaultHandler>> callBacks = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FeeService(FeeProvider feeProvider, DaoStateService daoStateService, PeriodService periodService) {
        this.feeProvider = feeProvider;
        FeeService.daoStateService = daoStateService;
        FeeService.periodService = periodService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(FilterManager providedFilterManager) {
        filterManager = providedFilterManager;
        minFeePerVByte = Config.baseCurrencyNetwork().getDefaultMinFeePerVbyte();

        requestFees();

        // We update all 5 min.
        UserThread.runPeriodically(this::requestFees, 5, TimeUnit.MINUTES);
    }


    public void requestFees() {
        requestFees(null, null);
    }

    public void requestFees(Runnable resultHandler) {
        requestFees(resultHandler, null);
    }

    public void requestFees(@Nullable Runnable resultHandler, @Nullable FaultHandler faultHandler) {
        callBacks.add(new Tuple2<>(resultHandler, faultHandler));
        if (hasPendingRequest()) {
            return;
        }

        long now = Instant.now().getEpochSecond();
        // Allow 1 request per 2 minutes
        if (now - lastRequest <= MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN * 60) {
            log.debug("We got a requestFees called again before min pause of {} minutes has passed.",
                    MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN);
            success();
        }

        lastRequest = now;
        FeeRequest feeRequest = new FeeRequest();
        SettableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> future = feeRequest.getFees(feeProvider);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Tuple2<Map<String, Long>, Map<String, Long>> result) {
                UserThread.execute(() -> {
                    checkNotNull(result, "Result must not be null at getFees");
                    timeStampMap = result.first;
                    epochInSecondAtLastRequest = timeStampMap.get(Config.BTC_FEES_TS);
                    final Map<String, Long> map = result.second;
                    txFeePerVbyte = map.get(Config.BTC_TX_FEE);
                    minFeePerVByte = map.get(Config.BTC_MIN_TX_FEE);

                    if (txFeePerVbyte < minFeePerVByte) {
                        log.warn("The delivered fee of {} sat/vbyte is smaller than the min. default fee of {} sat/vbyte", txFeePerVbyte, minFeePerVByte);
                        txFeePerVbyte = minFeePerVByte;
                    }

                    feeUpdateCounter.set(feeUpdateCounter.get() + 1);
                    log.info("BTC tx fee: txFeePerVbyte={} minFeePerVbyte={}", txFeePerVbyte, minFeePerVByte);
                    success();
                });
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.warn("Could not load fees. feeProvider={}, error={}", feeProvider.toString(), throwable.toString());
                fail(throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    // Clone callbacks, clear list and call all resultHandlers
    private void success() {
        var cloned = new ArrayList<>(callBacks);
        callBacks.clear();

        cloned.forEach(tuple -> {
            if (tuple.first != null) {
                UserThread.execute(tuple.first);
            }
        });
    }

    // Clone callbacks, clear list and call all faultHandlers
    private void fail(Throwable throwable) {
        var cloned = new ArrayList<>(callBacks);
        callBacks.clear();

        cloned.forEach(tuple -> {
            if (tuple.second != null) {
                UserThread.execute(() -> tuple.second.handleFault("Could not load fees", throwable));
            }
        });
    }

    public Coin getTxFee(int vsizeInVbytes) {
        return getTxFeePerVbyte().multiply(vsizeInVbytes);
    }

    public Coin getTxFeePerVbyte() {
        return Coin.valueOf(txFeePerVbyte);
    }

    public ReadOnlyIntegerProperty feeUpdateCounterProperty() {
        return feeUpdateCounter;
    }

    public boolean isFeeAvailable() {
        return feeUpdateCounter.get() > 0;
    }

    public boolean hasPendingRequest() {
        return feeProvider.getHttpClient().hasPendingRequest();
    }
}
