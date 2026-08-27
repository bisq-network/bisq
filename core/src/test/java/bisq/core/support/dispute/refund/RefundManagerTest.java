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

package bisq.core.support.dispute.refund;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class RefundManagerTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();

    private final RefundManager refundManager = mock(RefundManager.class, CALLS_REAL_METHODS);

    @Test
    void verifyTradeTxChainAcceptsDelayedPayoutTxSpendingDepositEscrowOutput() {
        List<Transaction> transactions = tradeTxChain(0);

        assertDoesNotThrow(() -> refundManager.verifyTradeTxChain(transactions));
    }

    @Test
    void verifyTradeTxChainRejectsDelayedPayoutTxSpendingDepositChangeOutput() {
        List<Transaction> transactions = tradeTxChain(1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> refundManager.verifyTradeTxChain(transactions));
        assertTrue(exception.getMessage().contains("output 0 of deposit tx"));
    }

    private static List<Transaction> tradeTxChain(int delayedPayoutInputIndex) {
        Transaction makerFeeTx = transactionWithOutput(Coin.valueOf(20_000));
        Transaction takerFeeTx = transactionWithOutput(Coin.valueOf(20_000));

        Transaction depositTx = new Transaction(PARAMS);
        depositTx.addInput(makerFeeTx.getOutput(0));
        depositTx.addInput(takerFeeTx.getOutput(0));
        depositTx.addOutput(Coin.valueOf(30_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        depositTx.addOutput(Coin.valueOf(9_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));

        Transaction delayedPayoutTx = new Transaction(PARAMS);
        delayedPayoutTx.addInput(depositTx.getOutput(delayedPayoutInputIndex));
        delayedPayoutTx.addOutput(Coin.valueOf(29_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));

        return List.of(makerFeeTx, takerFeeTx, depositTx, delayedPayoutTx);
    }

    private static Transaction transactionWithOutput(Coin value) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addOutput(value, ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        return transaction;
    }
}
