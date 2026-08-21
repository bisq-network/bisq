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

package bisq.core.trade;

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterManager;
import bisq.core.provider.fee.FeeService;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TradeFeeFactoryTest {
    private static final int CHAIN_HEIGHT = 102;

    @Test
    void getTradeTxFeeUsesTradeFeeAndDepositTxAverage() {
        assertEquals(Coin.valueOf(212), TradeFeeFactory.getTradeTxFee(Coin.valueOf(1)));
    }

    @Test
    void getTradeTxFeeRejectsInvalidFeeRate() {
        assertThrows(NullPointerException.class, () -> TradeFeeFactory.getTradeTxFee(null));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getTradeTxFee(Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getTradeTxFee(Coin.valueOf(-1)));
    }

    @Test
    void getMinerFeeByVsizeReturnsFeeForVsize() {
        assertEquals(Coin.valueOf(384), TradeFeeFactory.getMinerFeeByVsize(Coin.valueOf(2), 192));
    }

    @Test
    void getMinerFeeByVsizeRejectsInvalidFeeRate() {
        assertThrows(NullPointerException.class, () -> TradeFeeFactory.getMinerFeeByVsize(null, 192));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getMinerFeeByVsize(Coin.ZERO, 192));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getMinerFeeByVsize(Coin.valueOf(-1), 192));
    }

    @Test
    void getMinerFeeByVsizeRejectsInvalidVsize() {
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getMinerFeeByVsize(Coin.valueOf(2), 0));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getMinerFeeByVsize(Coin.valueOf(2), -1));
    }

    @Test
    void getMakerFeeUsesFeePerBtcOrMinimumFee() {
        configureFeeService(Coin.valueOf(10_000), Coin.valueOf(100), Coin.valueOf(20_000), Coin.valueOf(200));

        assertEquals(Coin.valueOf(10_000), TradeFeeFactory.getMakerFee(true, Coin.COIN));
        assertEquals(Coin.valueOf(100), TradeFeeFactory.getMakerFee(true, Coin.valueOf(1)));
    }

    @Test
    void getTakerFeeUsesFeePerBtcOrMinimumFee() {
        configureFeeService(Coin.valueOf(10_000), Coin.valueOf(100), Coin.valueOf(20_000), Coin.valueOf(200));

        assertEquals(Coin.valueOf(20_000), TradeFeeFactory.getTakerFee(false, Coin.COIN));
        assertEquals(Coin.valueOf(200), TradeFeeFactory.getTakerFee(false, Coin.valueOf(1)));
    }

    @Test
    void getTradeFeesRejectNullAmount() {
        assertThrows(NullPointerException.class, () -> TradeFeeFactory.getMakerFee(true, null));
        assertThrows(NullPointerException.class, () -> TradeFeeFactory.getTakerFee(true, null));
    }

    @Test
    void getTradeFeesRejectNonPositiveAmount() {
        configureFeeService(Coin.valueOf(10_000), Coin.valueOf(100), Coin.valueOf(20_000), Coin.valueOf(200));

        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getMakerFee(true, Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getMakerFee(true, Coin.valueOf(-1)));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getTakerFee(true, Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeFactory.getTakerFee(true, Coin.valueOf(-1)));
    }

    private static void configureFeeService(Coin makerFeePerBtc,
                                            Coin minMakerFee,
                                            Coin takerFeePerBtc,
                                            Coin minTakerFee) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        FilterManager filterManager = mock(FilterManager.class);
        when(periodService.getChainHeight()).thenReturn(CHAIN_HEIGHT);
        when(filterManager.getFilter()).thenReturn(null);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BTC, CHAIN_HEIGHT)).thenReturn(makerFeePerBtc);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BSQ, CHAIN_HEIGHT)).thenReturn(makerFeePerBtc);
        when(daoStateService.getParamValueAsCoin(Param.MIN_MAKER_FEE_BTC, CHAIN_HEIGHT)).thenReturn(minMakerFee);
        when(daoStateService.getParamValueAsCoin(Param.MIN_MAKER_FEE_BSQ, CHAIN_HEIGHT)).thenReturn(minMakerFee);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BTC, CHAIN_HEIGHT)).thenReturn(takerFeePerBtc);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BSQ, CHAIN_HEIGHT)).thenReturn(takerFeePerBtc);
        when(daoStateService.getParamValueAsCoin(Param.MIN_TAKER_FEE_BTC, CHAIN_HEIGHT)).thenReturn(minTakerFee);
        when(daoStateService.getParamValueAsCoin(Param.MIN_TAKER_FEE_BSQ, CHAIN_HEIGHT)).thenReturn(minTakerFee);
        new FeeService(daoStateService, periodService).onAllServicesInitialized(filterManager);
    }
}
