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

package bisq.core.offer;

import bisq.core.btc.wallet.BtcWalletService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import java.util.List;

import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(BtcWalletService.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class TxFeeEstimationTest {

    @Test
    public void testGetEstimatedTxSize() throws InsufficientMoneyException {
        List<Coin> outputValues = List.of(Coin.valueOf(2000), Coin.valueOf(3000));
        int initialEstimatedTxSize;
        Coin txFeePerByte;
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        int result;
        int realTxSize;
        Coin txFee;


        initialEstimatedTxSize = 260;
        txFeePerByte = Coin.valueOf(10);
        realTxSize = 260;

        txFee = txFeePerByte.multiply(initialEstimatedTxSize);
        when(btcWalletService.getEstimatedFeeTxSize(outputValues, txFee)).thenReturn(realTxSize);
        result = TxFeeEstimation.getEstimatedTxSize(outputValues, initialEstimatedTxSize, txFeePerByte, btcWalletService);
        assertEquals(260, result);


        // TODO check how to use the mocking framework for repeated calls
        // The btcWalletService.getEstimatedFeeTxSize returns 0 at repeated calls in the while loop....
       /* initialEstimatedTxSize = 260;
        txFeePerByte = Coin.valueOf(10);
        realTxSize = 2600;

        txFee = txFeePerByte.multiply(initialEstimatedTxSize);
        when(btcWalletService.getEstimatedFeeTxSize(outputValues, txFee)).thenReturn(realTxSize);
        result = TxFeeEstimation.getEstimatedTxSize(outputValues, initialEstimatedTxSize, txFeePerByte, btcWalletService);
        assertEquals(2600, result);

        initialEstimatedTxSize = 2600;
        txFeePerByte = Coin.valueOf(10);
        realTxSize = 260;

        txFee = txFeePerByte.multiply(initialEstimatedTxSize);
        when(btcWalletService.getEstimatedFeeTxSize(outputValues, txFee)).thenReturn(realTxSize);
        result = TxFeeEstimation.getEstimatedTxSize(outputValues, initialEstimatedTxSize, txFeePerByte, btcWalletService);
        assertEquals(260, result);*/
    }

    @Test
    public void testIsInTolerance() {
        int estimatedSize;
        int txSize;
        double tolerance;
        boolean result;

        estimatedSize = 100;
        txSize = 100;
        tolerance = 0.0001;
        result = TxFeeEstimation.isInTolerance(estimatedSize, txSize, tolerance);
        assertTrue(result);

        estimatedSize = 100;
        txSize = 200;
        tolerance = 0.2;
        result = TxFeeEstimation.isInTolerance(estimatedSize, txSize, tolerance);
        assertFalse(result);

        estimatedSize = 120;
        txSize = 100;
        tolerance = 0.2;
        result = TxFeeEstimation.isInTolerance(estimatedSize, txSize, tolerance);
        assertTrue(result);

        estimatedSize = 200;
        txSize = 100;
        tolerance = 1;
        result = TxFeeEstimation.isInTolerance(estimatedSize, txSize, tolerance);
        assertTrue(result);

        estimatedSize = 201;
        txSize = 100;
        tolerance = 1;
        result = TxFeeEstimation.isInTolerance(estimatedSize, txSize, tolerance);
        assertFalse(result);
    }
}
