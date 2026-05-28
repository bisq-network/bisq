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

import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterManager;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FeeServiceTest {

    // For now, we do not clamp to networkMin because of backward compatibility issues
    // @Test
    void updateFeeInfoClampsRatesToNetworkMin() {
        FeeService feeService = newFeeService();
        long networkMin = Config.baseCurrencyNetwork().getDefaultMinFeePerVbyte();

        feeService.updateFeeInfo(1, 1);

        assertEquals(networkMin, feeService.getTxFeePerVbyte().getValue());
        assertEquals(networkMin, feeService.getMinFeePerVByte());
    }

    @Test
    void updateFeeInfoCapsAbsurdProviderRates() {
        FeeService feeService = newFeeService();

        feeService.updateFeeInfo(Long.MAX_VALUE, Long.MAX_VALUE);

        assertEquals(FeeService.BTC_MAX_TX_FEE, feeService.getTxFeePerVbyte().getValue());
        assertEquals(FeeService.BTC_MAX_TX_FEE, feeService.getMinFeePerVByte());
        long expected = FeeService.BTC_MAX_TX_FEE * BsqSwapCalculation.ESTIMATED_V_BYTES - 3;
        long adjustedTxFee;
        try (MockedStatic<FeeService> mockedFeeService = mockStatic(FeeService.class)) {
            mockedFeeService.when(() -> FeeService.getMinMakerFee(false)).thenReturn(Coin.valueOf(3));
            mockedFeeService.when(() -> FeeService.getMinTakerFee(false)).thenReturn(Coin.valueOf(3));
            adjustedTxFee = BsqSwapCalculation.getAdjustedTxFee(feeService.getTxFeePerVbyte().getValue(),
                    BsqSwapCalculation.ESTIMATED_V_BYTES,
                    3);
        }
        assertEquals(expected, adjustedTxFee);
    }

    @Test
    void updateFeeInfoKeepsTxFeeAtLeastMinFeeAfterClamping() {
        FeeService feeService = newFeeService();
        // Derive provided values from networkMin so the assertion holds regardless of how
        // the network minimum is tuned (any value <= BTC_MAX_TX_FEE - 10).
        long networkMin = Config.baseCurrencyNetwork().getDefaultMinFeePerVbyte();
        long providedMin = Math.min(networkMin + 10, FeeService.BTC_MAX_TX_FEE);
        long providedTx = Math.max(networkMin, providedMin - 50);

        feeService.updateFeeInfo(providedTx, providedMin);

        assertEquals(providedMin, feeService.getTxFeePerVbyte().getValue());
        assertEquals(providedMin, feeService.getMinFeePerVByte());
    }

    private static FeeService newFeeService() {
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        FilterManager filterManager = mock(FilterManager.class);
        when(filterManager.getFilter()).thenReturn(null);

        FeeService feeService = new FeeService(daoStateService, periodService);
        feeService.onAllServicesInitialized(filterManager);
        return feeService;
    }
}
