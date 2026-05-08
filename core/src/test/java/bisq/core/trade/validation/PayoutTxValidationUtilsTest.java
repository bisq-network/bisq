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

package bisq.core.trade.validation;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.MainNetParams;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayoutTxValidationUtilsTest {
    private static final MainNetParams PARAMS = MainNetParams.get();
    private static final String BUYER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    private static final String SELLER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();


    /* --------------------------------------------------------------------- */
    // Payout transaction input
    /* --------------------------------------------------------------------- */

    @Test
    void checkPayoutTxInputSpendsDepositOutputZeroAcceptsMatchingInput() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(6_000),
                BUYER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertSame(payoutTx, PayoutTxValidationUtils.checkPayoutTxInputSpendsDepositOutputZero(payoutTx,
                depositTx,
                "payoutTx"));
    }

    @Test
    void checkPayoutTxInputSpendsDepositOutputZeroRejectsDifferentDepositTx() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction otherDepositTx = depositTx(Coin.valueOf(12_000));
        Transaction payoutTx = payoutTx(otherDepositTx,
                Coin.valueOf(6_000),
                BUYER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertThrows(IllegalArgumentException.class,
                () -> PayoutTxValidationUtils.checkPayoutTxInputSpendsDepositOutputZero(payoutTx,
                        depositTx,
                        "payoutTx"));
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction output sum
    /* --------------------------------------------------------------------- */

    @Test
    void checkPayoutTxOutputSumNotGreaterThanDepositOutputValueAcceptsCoveredPayoutAmounts() {
        Transaction depositTx = depositTx(Coin.valueOf(10_000));

        assertSame(depositTx,
                PayoutTxValidationUtils.checkPayoutTxOutputSumNotGreaterThanDepositOutputValue(depositTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        "payoutTx"));
    }

    @Test
    void checkPayoutTxOutputSumNotGreaterThanDepositOutputValueRejectsOverspend() {
        Transaction depositTx = depositTx(Coin.valueOf(9_999));

        assertThrows(IllegalArgumentException.class,
                () -> PayoutTxValidationUtils.checkPayoutTxOutputSumNotGreaterThanDepositOutputValue(depositTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        "payoutTx"));
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction outputs
    /* --------------------------------------------------------------------- */

    @Test
    void checkPayoutTxOutputAmountsAndAddressesAcceptsExpectedOutputs() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(6_000),
                BUYER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertSame(payoutTx,
                PayoutTxValidationUtils.checkPayoutTxOutputAmountsAndAddresses(payoutTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS,
                        "payoutTx",
                        "payoutTx must have at least one positive payout amount"));
    }

    @Test
    void checkPayoutTxOutputAmountsAndAddressesRejectsMissingPositivePayoutAmount() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.ZERO,
                BUYER_ADDRESS,
                Coin.ZERO,
                SELLER_ADDRESS);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PayoutTxValidationUtils.checkPayoutTxOutputAmountsAndAddresses(payoutTx,
                        Coin.ZERO,
                        Coin.ZERO,
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS,
                        "payoutTx",
                        "payoutTx must have at least one positive payout amount"));

        assertEquals("payoutTx must have at least one positive payout amount", exception.getMessage());
    }

    @Test
    void checkPayoutTxOutputAmountsAndAddressesRejectsWrongOutputAmount() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(5_999),
                BUYER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertThrows(IllegalArgumentException.class,
                () -> PayoutTxValidationUtils.checkPayoutTxOutputAmountsAndAddresses(payoutTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS,
                        "payoutTx",
                        "payoutTx must have at least one positive payout amount"));
    }

    @Test
    void checkPayoutTxOutputAmountsAndAddressesRejectsWrongOutputAddress() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(6_000),
                SELLER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertThrows(IllegalArgumentException.class,
                () -> PayoutTxValidationUtils.checkPayoutTxOutputAmountsAndAddresses(payoutTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS,
                        "payoutTx",
                        "payoutTx must have at least one positive payout amount"));
    }

    private static Transaction depositTx(Coin outputAmount) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(new TransactionInput(PARAMS,
                transaction,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.ZERO_HASH),
                outputAmount.add(Coin.SATOSHI)));
        transaction.addOutput(outputAmount, Address.fromString(PARAMS, BUYER_ADDRESS));
        return transaction;
    }

    private static Transaction payoutTx(Transaction depositTx,
                                        Coin buyerPayoutAmount,
                                        String buyerPayoutAddressString,
                                        Coin sellerPayoutAmount,
                                        String sellerPayoutAddressString) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(depositTx.getOutput(0));
        if (buyerPayoutAmount.isPositive()) {
            transaction.addOutput(buyerPayoutAmount, Address.fromString(PARAMS, buyerPayoutAddressString));
        }
        if (sellerPayoutAmount.isPositive()) {
            transaction.addOutput(sellerPayoutAmount, Address.fromString(PARAMS, sellerPayoutAddressString));
        }
        return transaction;
    }
}
