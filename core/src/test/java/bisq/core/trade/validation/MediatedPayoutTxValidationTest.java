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

import static bisq.core.trade.validation.MediatedPayoutTxValidation.checkMediatedPayoutAddresses;
import static bisq.core.trade.validation.MediatedPayoutTxValidation.checkMediatedPayoutAmounts;
import static bisq.core.trade.validation.MediatedPayoutTxValidation.checkMediatedPayoutTx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediatedPayoutTxValidationTest {
    private static final MainNetParams PARAMS = MainNetParams.get();
    private static final String BUYER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    private static final String SELLER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();

    /* --------------------------------------------------------------------- */
    // Mediation result amounts
    /* --------------------------------------------------------------------- */

    @Test
    void checkMediatedPayoutAmountsAcceptsMediationResultTotal() {
        checkMediatedPayoutAmounts(Coin.valueOf(6_000),
                Coin.valueOf(4_000),
                Coin.valueOf(10_000));
    }

    @Test
    void checkMediatedPayoutAmountsRejectsMismatchingTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> checkMediatedPayoutAmounts(Coin.valueOf(6_000),
                        Coin.valueOf(3_999),
                        Coin.valueOf(10_000)));
    }

    /* --------------------------------------------------------------------- */
    // Mediation result addresses
    /* --------------------------------------------------------------------- */

    @Test
    void checkMediatedPayoutAddressesAcceptsValidAddressesForPositiveAmounts() {
        checkMediatedPayoutAddresses(BUYER_ADDRESS,
                Coin.valueOf(6_000),
                SELLER_ADDRESS,
                Coin.valueOf(4_000),
                ValidationTestUtils.btcWalletService());
    }

    @Test
    void checkMediatedPayoutAddressesRejectsInvalidAddressForPositiveAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> checkMediatedPayoutAddresses("not-a-bitcoin-address",
                        Coin.valueOf(6_000),
                        SELLER_ADDRESS,
                        Coin.valueOf(4_000),
                        ValidationTestUtils.btcWalletService()));
    }

    @Test
    void checkMediatedPayoutAddressesRejectsBlankAddressEvenForZeroAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> checkMediatedPayoutAddresses(" ",
                        Coin.ZERO,
                        SELLER_ADDRESS,
                        Coin.valueOf(4_000),
                        ValidationTestUtils.btcWalletService()));
    }

    /* --------------------------------------------------------------------- */
    // Mediated payout tx
    /* --------------------------------------------------------------------- */

    @Test
    void checkMediatedPayoutTxAcceptsExpectedInputOutputsAndAddresses() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(6_000),
                BUYER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertSame(payoutTx,
                checkMediatedPayoutTx(payoutTx,
                        depositTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS));
    }

    @Test
    void checkMediatedPayoutTxAcceptsSingleOutputWhenBuyerAmountIsZero() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.ZERO,
                BUYER_ADDRESS,
                Coin.valueOf(10_000),
                SELLER_ADDRESS);

        assertSame(payoutTx,
                checkMediatedPayoutTx(payoutTx,
                        depositTx,
                        Coin.ZERO,
                        Coin.valueOf(10_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS));
    }

    @Test
    void checkMediatedPayoutTxRejectsOutputSumAboveDepositOutputValue() {
        Transaction depositTx = depositTx(Coin.valueOf(9_999));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(6_000),
                BUYER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertThrows(IllegalArgumentException.class,
                () -> checkMediatedPayoutTx(payoutTx,
                        depositTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS));
    }

    @Test
    void checkMediatedPayoutTxRejectsZeroBuyerAndSellerPayoutAmounts() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(1_000),
                BUYER_ADDRESS,
                Coin.ZERO,
                SELLER_ADDRESS);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkMediatedPayoutTx(payoutTx,
                        depositTx,
                        Coin.ZERO,
                        Coin.ZERO,
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS));

        assertEquals("Mediated payout tx must have at least one positive payout amount",
                exception.getMessage());
    }

    @Test
    void checkMediatedPayoutTxRejectsInputNotSpendingDepositOutputZero() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction otherDepositTx = depositTx(Coin.valueOf(12_000));
        Transaction payoutTx = payoutTx(otherDepositTx,
                Coin.valueOf(6_000),
                BUYER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertThrows(IllegalArgumentException.class,
                () -> checkMediatedPayoutTx(payoutTx,
                        depositTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS));
    }

    @Test
    void checkMediatedPayoutTxRejectsWrongBuyerOutputAmount() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(5_999),
                BUYER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertThrows(IllegalArgumentException.class,
                () -> checkMediatedPayoutTx(payoutTx,
                        depositTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS));
    }

    @Test
    void checkMediatedPayoutTxRejectsWrongBuyerOutputAddress() {
        Transaction depositTx = depositTx(Coin.valueOf(11_000));
        Transaction payoutTx = payoutTx(depositTx,
                Coin.valueOf(6_000),
                SELLER_ADDRESS,
                Coin.valueOf(4_000),
                SELLER_ADDRESS);

        assertThrows(IllegalArgumentException.class,
                () -> checkMediatedPayoutTx(payoutTx,
                        depositTx,
                        Coin.valueOf(6_000),
                        Coin.valueOf(4_000),
                        BUYER_ADDRESS,
                        SELLER_ADDRESS,
                        PARAMS));
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
