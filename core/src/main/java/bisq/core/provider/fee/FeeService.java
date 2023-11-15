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

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.time.Instant;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeeService {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Miner fees are between 1-600 sat/vbyte. We try to stay on the safe side. BTC_DEFAULT_TX_FEE is only used if our
    // fee service would not deliver data.
    private static final long BTC_DEFAULT_TX_FEE = 50;
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

    private final IntegerProperty feeUpdateCounter = new SimpleIntegerProperty(0);
    private long txFeePerVbyte = BTC_DEFAULT_TX_FEE;
    @Getter
    private long lastRequest = 0;
    @Getter
    private long minFeePerVByte;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FeeService(DaoStateService daoStateService, PeriodService periodService) {
        FeeService.daoStateService = daoStateService;
        FeeService.periodService = periodService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(FilterManager providedFilterManager) {
        filterManager = providedFilterManager;
        minFeePerVByte = Config.baseCurrencyNetwork().getDefaultMinFeePerVbyte();
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

    public void updateFeeInfo(long txFeePerVbyte, long minFeePerVByte) {
        this.txFeePerVbyte = txFeePerVbyte;
        this.minFeePerVByte = minFeePerVByte;
        feeUpdateCounter.set(feeUpdateCounter.get() + 1);
        lastRequest = Instant.now().getEpochSecond();
        log.info("BTC tx fee: txFeePerVbyte={} minFeePerVbyte={}", txFeePerVbyte, minFeePerVByte);
    }
}
