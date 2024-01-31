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
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.provider.mempool.FeeValidationStatus;
import bisq.core.provider.mempool.TxValidator;

import org.bitcoinj.core.Coin;

import java.util.List;
import java.util.Optional;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
public class TxValidatorMakerBsqFeeTests {

    private static final int FEE_BLOCK_THRESHOLD = 48;
    private static final int FEE_PAYMENT_HEIGHT = 143;
    private static final String FEE_TX_ID = "e3607e971ead7d03619e3a9eeaa771ed5adba14c448839e0299f857f7bb4ec07";

    @Test
    void feeUnconfirmedAfterEightHours(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        doReturn(Optional.empty()).when(daoStateService).getTx(anyString());

        int chainHeight = FEE_PAYMENT_HEIGHT + FEE_BLOCK_THRESHOLD + 2;
        doReturn(chainHeight).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_BSQ_FEE_NOT_FOUND));
    }

    @Test
    void newBsqTx(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        doReturn(Optional.empty()).when(daoStateService).getTx(anyString());
        doReturn(FEE_PAYMENT_HEIGHT).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_BSQ_TX_IS_NEW));
    }

    @Test
    void makerFeeAmountExactMatch(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        Tx tx = mock(Tx.class);
        doReturn(5000L).when(tx).getBurntBsq();
        doReturn(Optional.of(tx)).when(daoStateService).getTx(anyString());

        doReturn(FEE_PAYMENT_HEIGHT).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerFeeAmountHigherThanExpected(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        Tx tx = mock(Tx.class);
        doReturn(6000L).when(tx).getBurntBsq();
        doReturn(Optional.of(tx)).when(daoStateService).getTx(anyString());

        doReturn(FEE_PAYMENT_HEIGHT).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerFeeWithinTolerance(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        Tx tx = mock(Tx.class);
        doReturn(4900L).when(tx).getBurntBsq();
        doReturn(Optional.of(tx)).when(daoStateService).getTx(anyString());

        doReturn(FEE_PAYMENT_HEIGHT).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(5000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerPassFilterFeeCheck(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        Tx tx = mock(Tx.class);
        doReturn(4900L).when(tx).getBurntBsq();
        doReturn(Optional.of(tx)).when(daoStateService).getTx(anyString());

        doReturn(FEE_PAYMENT_HEIGHT).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        doReturn(Coin.valueOf(10_000), Coin.valueOf(5_000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(10_000), Coin.valueOf(5_000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        Filter filter = mock(Filter.class);
        doReturn(5_000_000L).when(filter).getMakerFeeBsq();
        doReturn(filter).when(filterManager).getFilter();

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerFailFilterFeeCheck(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        Tx tx = mock(Tx.class);
        doReturn(4900L).when(tx).getBurntBsq();
        doReturn(Optional.of(tx)).when(daoStateService).getTx(anyString());

        doReturn(FEE_PAYMENT_HEIGHT).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        doReturn(Coin.valueOf(10_000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(10_000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        Filter filter = mock(Filter.class);
        doReturn(10_000_000L).when(filter).getMakerFeeBsq();
        doReturn(filter).when(filterManager).getFilter();

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_MAKER_FEE_TOO_LOW));
    }

    @Test
    void makerNoFilterFeeMatchesDifferentDaoParameter(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        Tx tx = mock(Tx.class);
        doReturn(4900L).when(tx).getBurntBsq();
        doReturn(Optional.of(tx)).when(daoStateService).getTx(anyString());

        doReturn(FEE_PAYMENT_HEIGHT).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        doReturn(Coin.valueOf(10_000), Coin.valueOf(4900)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(10_000), Coin.valueOf(4900)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        doReturn(null).when(filterManager).getFilter();
        doReturn(List.of(Coin.valueOf(4900))).when(daoStateService).getParamChangeList(any());

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.ACK_FEE_OK));
    }

    @Test
    void makerNoFilterFeeTooLow(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        Tx tx = mock(Tx.class);
        doReturn(4900L).when(tx).getBurntBsq();
        doReturn(Optional.of(tx)).when(daoStateService).getTx(anyString());

        doReturn(FEE_PAYMENT_HEIGHT).when(daoStateService).getChainHeight();

        TxValidator txValidator = new TxValidator(daoStateService, FEE_TX_ID,
                Coin.valueOf(100000), true, FEE_PAYMENT_HEIGHT, filterManager);

        doReturn(Coin.valueOf(10_000)).when(daoStateService).getParamValueAsCoin(any(), anyInt());
        doReturn(Coin.valueOf(10_000)).when(daoStateService).getParamValueAsCoin(any(), anyString());

        doReturn(null).when(filterManager).getFilter();
        doReturn(List.of(Coin.valueOf(5_000))).when(daoStateService).getParamChangeList(any());

        TxValidator txValidator1 = txValidator.validateBsqFeeTx(true);
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_MAKER_FEE_TOO_LOW));
    }
}
