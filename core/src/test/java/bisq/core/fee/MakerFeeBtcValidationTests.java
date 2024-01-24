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

package bisq.core.fee;

import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.provider.mempool.FeeValidationStatus;
import bisq.core.provider.mempool.TxValidator;

import org.bitcoinj.core.Coin;

import java.util.Collections;
import java.util.List;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class MakerFeeBtcValidationTests {
    private static final List<String> BTC_FEE_RECEIVERS = List.of("2MzBNTJDjjXgViKBGnatDU3yWkJ8pJkEg9w");
    private static final String VALID_MAKER_FEE_JSON_RESPONSE = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponseString();

    @Mock
    private DaoStateService daoStateService;
    @Mock
    private FilterManager filterManager;
    private TxValidator txValidator;

    @BeforeEach
    void setup() {
        String txId = "e3607e971ead7d03619e3a9eeaa771ed5adba14c448839e0299f857f7bb4ec07";
        txValidator = new TxValidator(daoStateService, txId,
                Coin.valueOf(100000), true, 143, filterManager);
    }

    @Test
    void makerCheckFeeAddressBtcTooOldValidFee() {
        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(VALID_MAKER_FEE_JSON_RESPONSE, Collections.emptyList());
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerExactFeeMatchBtcTest() {
        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(VALID_MAKER_FEE_JSON_RESPONSE, BTC_FEE_RECEIVERS);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerHigherBtcFeeThanExpected() {
        doReturn(Coin.valueOf(4000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(4000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(VALID_MAKER_FEE_JSON_RESPONSE, BTC_FEE_RECEIVERS);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerLowerButWithinToleranceBtcFee() {
        doReturn(Coin.valueOf(5001)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(5001)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(VALID_MAKER_FEE_JSON_RESPONSE, BTC_FEE_RECEIVERS);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerPassFilterFeeCheck() {
        doReturn(Coin.valueOf(10_000), Coin.valueOf(5_000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(10_000), Coin.valueOf(5_000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        Filter filter = mock(Filter.class);
        doReturn(5_000_000L).when(filter).getMakerFeeBtc();
        doReturn(filter).when(filterManager).getFilter();

        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(VALID_MAKER_FEE_JSON_RESPONSE, BTC_FEE_RECEIVERS);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerFailFilterFeeCheck() {
        doReturn(Coin.valueOf(10_000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(10_000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        Filter filter = mock(Filter.class);
        doReturn(10_000_000L).when(filter).getMakerFeeBtc();
        doReturn(filter).when(filterManager).getFilter();

        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(VALID_MAKER_FEE_JSON_RESPONSE, BTC_FEE_RECEIVERS);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_MAKER_FEE_TOO_LOW));
    }

    @Test
    void makerNoFilterFeeMatchesDifferentDaoParameter() {
        doReturn(Coin.valueOf(10_000), Coin.valueOf(5_000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(10_000), Coin.valueOf(5_000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        doReturn(null).when(filterManager).getFilter();
        doReturn(List.of(Coin.valueOf(5_000))).when(daoStateService).getParamChangeList(any());

        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(VALID_MAKER_FEE_JSON_RESPONSE, BTC_FEE_RECEIVERS);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerNoFilterFeeTooLow() {
        doReturn(Coin.valueOf(10_000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(10_000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        doReturn(null).when(filterManager).getFilter();
        doReturn(List.of(Coin.valueOf(5_000))).when(daoStateService).getParamChangeList(any());

        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(VALID_MAKER_FEE_JSON_RESPONSE, BTC_FEE_RECEIVERS);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_MAKER_FEE_TOO_LOW));
    }
}
