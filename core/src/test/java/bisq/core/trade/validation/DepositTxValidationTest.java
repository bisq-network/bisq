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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.validation;

import bisq.core.btc.wallet.BtcWalletService;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.DepositTxValidation.checkDepositTxMatchesIgnoringWitnessesAndScriptSigs;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DepositTxValidationTest {
    private static final MainNetParams PARAMS = MainNetParams.get();
    private static final String BUYER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    private static final String SELLER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsAcceptsMatchingTxsAndReturnsDepositTx() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = copy(expectedDepositTx);
        addSignatureData(expectedDepositTx, new byte[]{1, 2}, new byte[]{3});
        addSignatureData(depositTx, new byte[]{4, 5}, new byte[]{6});

        assertSame(depositTx,
                checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentInputOutpoint() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 1);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentOutputAmount() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(9_999), BUYER_ADDRESS, 0);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentOutputAddress() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(10_000), SELLER_ADDRESS, 0);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsNullPublicArgs() {
        Transaction depositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        BtcWalletService btcWalletService = btcWalletService();

        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(null,
                        depositTx,
                        btcWalletService));
        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        null,
                        btcWalletService));
        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        depositTx,
                        (BtcWalletService) null));
    }

    private static Transaction depositTx(Coin outputAmount, String addressString, long outpointIndex) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(new TransactionInput(PARAMS,
                transaction,
                new byte[]{},
                new TransactionOutPoint(PARAMS, outpointIndex, Sha256Hash.ZERO_HASH),
                outputAmount.add(Coin.SATOSHI)));
        transaction.addOutput(outputAmount, Address.fromString(PARAMS, addressString));
        return transaction;
    }

    private static void addSignatureData(Transaction transaction, byte[] scriptSigProgram, byte[] witnessProgram) {
        transaction.getInput(0).setScriptSig(new ScriptBuilder().data(scriptSigProgram).build());
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, witnessProgram);
        transaction.getInput(0).setWitness(witness);
    }

    private static Transaction copy(Transaction transaction) {
        return new Transaction(PARAMS, transaction.bitcoinSerialize());
    }

    private static BtcWalletService btcWalletService() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        return btcWalletService;
    }
}
