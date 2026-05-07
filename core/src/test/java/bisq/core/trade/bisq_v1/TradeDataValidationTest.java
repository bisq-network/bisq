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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.bisq_v1;

import bisq.core.btc.model.RawTransactionInput;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TradeDataValidationTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();

    @Test
    void assertCanonicalDepositTxShapeAcceptsCanonicalTx() {
        assertDoesNotThrow(() -> TradeDataValidation.assertCanonicalDepositTxShape(
                canonicalTx(), p2wpkhInputs(), PARAMS));
    }

    @Test
    void assertCanonicalDepositTxShapeRejectsNonV1Tx() {
        Transaction tx = canonicalTx();
        tx.setVersion(2);

        assertThrows(TradeDataValidation.InvalidTxException.class,
                () -> TradeDataValidation.assertCanonicalDepositTxShape(tx, p2wpkhInputs(), PARAMS));
    }

    @Test
    void assertCanonicalDepositTxShapeRejectsNonZeroLockTime() {
        Transaction tx = canonicalTx();
        tx.setLockTime(1);

        assertThrows(TradeDataValidation.InvalidTxException.class,
                () -> TradeDataValidation.assertCanonicalDepositTxShape(tx, p2wpkhInputs(), PARAMS));
    }

    @Test
    void assertCanonicalDepositTxShapeRejectsRbfEnabledSequence() {
        Transaction tx = canonicalTx();
        tx.getInput(0).setSequenceNumber(0);

        assertThrows(TradeDataValidation.InvalidTxException.class,
                () -> TradeDataValidation.assertCanonicalDepositTxShape(tx, p2wpkhInputs(), PARAMS));
    }

    @Test
    void assertCanonicalDepositTxShapeAcceptsLockTimeOptInSequence() {
        // NO_SEQUENCE - 1 (0xFFFFFFFE) keeps RBF disabled while opting in to lockTime; canonical for Bisq.
        Transaction tx = canonicalTx();
        tx.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE - 1);

        assertDoesNotThrow(() -> TradeDataValidation.assertCanonicalDepositTxShape(tx, p2wpkhInputs(), PARAMS));
    }

    @Test
    void assertCanonicalDepositTxShapeRejectsNonP2WPKHPeerInput() {
        assertThrows(TradeDataValidation.InvalidTxException.class,
                () -> TradeDataValidation.assertCanonicalDepositTxShape(canonicalTx(), p2pkhInputs(), PARAMS));
    }

    @Test
    void assertCanonicalDepositTxShapeRejectsNullPeerInput() {
        assertThrows(TradeDataValidation.InvalidTxException.class,
                () -> TradeDataValidation.assertCanonicalDepositTxShape(canonicalTx(),
                        java.util.Collections.singletonList(null),
                        PARAMS));
    }

    private static Transaction canonicalTx() {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(new TransactionInput(PARAMS,
                tx,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.ZERO_HASH),
                Coin.valueOf(2_000)));
        return tx;
    }

    private static List<RawTransactionInput> p2wpkhInputs() {
        return parentTxInputs(ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
    }

    private static List<RawTransactionInput> p2pkhInputs() {
        return parentTxInputs(ScriptBuilder.createOutputScript(LegacyAddress.fromKey(PARAMS, new ECKey())));
    }

    private static List<RawTransactionInput> parentTxInputs(org.bitcoinj.script.Script outputScript) {
        Transaction parent = new Transaction(PARAMS);
        // Bitcoinj's segwit serialization requires at least one input; add a dummy.
        parent.addInput(new TransactionInput(PARAMS,
                parent,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.ZERO_HASH),
                Coin.valueOf(2_000)));
        parent.addOutput(Coin.valueOf(1_000), outputScript);
        return List.of(new RawTransactionInput(0, parent.bitcoinSerialize(), 1_000));
    }

}
