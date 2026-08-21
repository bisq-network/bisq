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

package bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker;

import bisq.core.btc.exceptions.TransactionVerificationException;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.RegTestParams;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SellerAsTakerSignsDepositTxTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();

    @Test
    void preparedDepositTxFromBuyerAsMakerAcceptsNoMakerChange() {
        Transaction makersDepositTx = txWithOutputs(140_000);

        assertDoesNotThrow(() -> SellerAsTakerSignsDepositTx.verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx));
    }

    @Test
    void preparedDepositTxFromBuyerAsMakerRejectsMakerChangeOutput() {
        Transaction makersDepositTx = txWithOutputs(140_000, 5_000);

        assertThrows(TransactionVerificationException.class,
                () -> SellerAsTakerSignsDepositTx.verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx));
    }

    @Test
    void preparedDepositTxFromBuyerAsMakerRejectsZeroValuedSecondOutput() {
        Transaction makersDepositTx = txWithOutputs(140_000, 0);

        assertThrows(TransactionVerificationException.class,
                () -> SellerAsTakerSignsDepositTx.verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx));
    }

    @Test
    void preparedDepositTxFromBuyerAsMakerRejectsMoreThanOneExtraOutput() {
        Transaction makersDepositTx = txWithOutputs(140_000, 5_000, 1_000);

        assertThrows(TransactionVerificationException.class,
                () -> SellerAsTakerSignsDepositTx.verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx));
    }

    private static Transaction txWithOutputs(long... outputValues) {
        Transaction tx = new Transaction(PARAMS);
        for (long outputValue : outputValues) {
            tx.addOutput(Coin.valueOf(outputValue), SegwitAddress.fromKey(PARAMS, new ECKey()));
        }
        return tx;
    }
}
