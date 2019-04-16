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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO use dao parameters for fee
@Slf4j
public class FeeService {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Miner fees are between 1-600 sat/byte. We try to stay on the safe side. BTC_DEFAULT_TX_FEE is only used if our
    // fee service would not deliver data.
    private static final long BTC_DEFAULT_TX_FEE = 50;
    private static final long MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN = 2;
    private static DaoStateService daoStateService;
    private static PeriodService periodService;

    private static Coin getFeeFromParamAsCoin(Param parm) {
        return daoStateService != null && periodService != null ? daoStateService.getParamValueAsCoin(parm, periodService.getChainHeight()) : Coin.ZERO;
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
    private long txFeePerByte = BTC_DEFAULT_TX_FEE;
    private Map<String, Long> timeStampMap;
    private long lastRequest;
    private long minFeePerByte;
    private long epochInSecondAtLastRequest;


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

    public void onAllServicesInitialized() {
        minFeePerByte = Config.baseCurrencyNetwork().getDefaultMinFeePerByte();

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
        long now = Instant.now().getEpochSecond();
        // We all requests only each 2 minutes
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
                        txFeePerByte = map.get("BTC");

                        if (txFeePerByte < minFeePerByte) {
                            log.warn("The delivered fee per byte is smaller than the min. default fee of 5 sat/byte");
                            txFeePerByte = minFeePerByte;
                        }

                        feeUpdateCounter.set(feeUpdateCounter.get() + 1);
                        log.info("BTC tx fee: txFeePerByte={}", txFeePerByte);
                        if (resultHandler != null)
                            resultHandler.run();
                    });
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.warn("Could not load fees. feeProvider={}, error={}", feeProvider.toString(), throwable.toString());
                    if (faultHandler != null)
                        UserThread.execute(() -> faultHandler.handleFault("Could not load fees", throwable));
                }
            }, MoreExecutors.directExecutor());
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

    public ReadOnlyIntegerProperty feeUpdateCounterProperty() {
        return feeUpdateCounter;
    }
}
