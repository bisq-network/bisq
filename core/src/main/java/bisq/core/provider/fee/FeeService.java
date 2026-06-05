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
import bisq.core.filter.DenyList;
import bisq.core.filter.FilterManager;
import bisq.core.filter.FilterPolicyService;
import bisq.core.util.Validator;

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
    static final long BTC_MAX_TX_FEE = 600;
    private static DaoStateService daoStateService;
    private static PeriodService periodService;
    private static FilterPolicyService filterPolicyService = null;

    private static Coin getFeeFromParamAsCoin(Param param) {
        // if specified, filter values take precedence
        Coin fromFilter = getFilterFromParamAsCoin(param);
        if (fromFilter.isGreaterThan(Coin.ZERO)) {
            return fromFilter;
        }
        return daoStateService != null && periodService != null ? daoStateService.getParamValueAsCoin(param, periodService.getChainHeight()) : Coin.ZERO;
    }

    private static Coin getFilterFromParamAsCoin(Param param) {
        if (filterPolicyService == null) {
            return Coin.ZERO;
        }
        if (param == Param.DEFAULT_MAKER_FEE_BTC) {
            return filterPolicyService.getFeeFromFilter(true, true).orElse(Coin.ZERO);
        } else if (param == Param.DEFAULT_TAKER_FEE_BTC) {
            return filterPolicyService.getFeeFromFilter(false, true).orElse(Coin.ZERO);
        } else if (param == Param.DEFAULT_MAKER_FEE_BSQ) {
            return filterPolicyService.getFeeFromFilter(true, false).orElse(Coin.ZERO);
        } else if (param == Param.DEFAULT_TAKER_FEE_BSQ) {
            return filterPolicyService.getFeeFromFilter(false, false).orElse(Coin.ZERO);
        }
        return Coin.ZERO;
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

    public void onAllServicesInitialized() {
        minFeePerVByte = Config.baseCurrencyNetwork().getDefaultMinFeePerVbyte();
    }

    public void onAllServicesInitialized(FilterPolicyService providedFilterPolicyService) {
        filterPolicyService = providedFilterPolicyService;
        onAllServicesInitialized();
    }

    public void onAllServicesInitialized(FilterManager providedFilterManager) {
        filterPolicyService = new FilterPolicyService(DenyList.empty(), providedFilterManager);
        onAllServicesInitialized();
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
        Validator.checkIsPositive(txFeePerVbyte, "txFeePerVbyte");
        Validator.checkIsPositive(minFeePerVByte, "minFeePerVByte");
        // Defense-in-depth: a misbehaving or malicious fee provider could push values outside
        // the realistic range downstream code assumes. Clamp both fields to the network
        // default min and cap them at the documented high-water mark so the invariant
        // BTC_MAX_TX_FEE >= txFeePerVbyte >= minFeePerVByte >= networkMin holds for every
        // consumer. This matters for BSQ-swap arithmetic where getAdjustedTxFee relies on
        // txFeePerVbyte >= minFeePerVByte to guarantee total tx fee meets relay minimum
        // when one side's BSQ trade fee exceeds its miner-portion (see BsqSwapCalculation),
        // and avoids multiply overflow in fee estimators if a bad feed returns absurd values.
        long networkMin = Config.baseCurrencyNetwork().getDefaultMinFeePerVbyte();

        // v1.10.0 does not clamp to networkMin, thus we can receive lower values (usually 10).
        // To not break backward compatibility, we only clamp to BTC_MAX_TX_FEE.
        // Once trade version has enforces > v1.10.0 we can use the networkMin as lower bound as well.
        // long clampedMinFee = Math.min(Math.max(minFeePerVByte, networkMin), BTC_MAX_TX_FEE);
        long clampedMinFee = Math.min(minFeePerVByte, BTC_MAX_TX_FEE);
        long clampedTxFee = Math.min(Math.max(txFeePerVbyte, clampedMinFee), BTC_MAX_TX_FEE);
        if (clampedTxFee != txFeePerVbyte || clampedMinFee != minFeePerVByte) {
            log.warn("Fee provider returned txFeePerVbyte={}, minFeePerVbyte={}, networkMin={}, maxFeePerVbyte={}; " +
                            "clamped to txFeePerVbyte={}, minFeePerVbyte={} to enforce " +
                            "maxFeePerVbyte>=txFeePerVbyte>=minFeePerVbyte>=networkMin",
                    txFeePerVbyte, minFeePerVByte, networkMin, BTC_MAX_TX_FEE, clampedTxFee, clampedMinFee);
        }
        this.txFeePerVbyte = clampedTxFee;
        this.minFeePerVByte = clampedMinFee;
        feeUpdateCounter.set(feeUpdateCounter.get() + 1);
        lastRequest = Instant.now().getEpochSecond();
        log.info("BTC tx fee: txFeePerVbyte={} minFeePerVbyte={}", this.txFeePerVbyte, this.minFeePerVByte);
    }
}
