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

package bisq.core.trade.protocol.bsq_swap.model;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BsqSwapPublicationTest {
    @Test
    void handoffCopiesBytesAndDoesNotAuthorizeAnotherTransaction() {
        var model = new BsqSwapProtocolModel(mock(PubKeyRing.class));
        byte[] submitted = {1, 2, 3};
        model.setTx(submitted);
        assertFalse(model.hasTransactionPublication(submitted));

        model.recordTransactionPublication(submitted);
        submitted[0] = 4;

        assertTrue(model.hasTransactionPublication(new byte[]{1, 2, 3}));
        assertFalse(model.hasTransactionPublication(submitted));
        model.setTx(new byte[]{5, 6, 7});
        assertFalse(model.hasTransactionPublication(model.getTx()));
    }

    @Test
    void sameTxIdWithDifferentWitnessDoesNotMatchHandoff() {
        var params = RegTestParams.get();
        Context.propagate(new Context(params));
        Transaction tx = new Transaction(params);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        tx.addOutput(Coin.valueOf(1000), new Script(new byte[0]));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{1});
        tx.getInput(0).setWitness(witness);
        Sha256Hash txId = tx.getTxId();
        var model = new BsqSwapProtocolModel(mock(PubKeyRing.class));
        model.applyTransaction(tx);
        assertFalse(model.hasTransactionPublication(model.getTx()));
        model.recordTransactionPublication(model.getTx());

        TransactionWitness changedWitness = new TransactionWitness(1);
        changedWitness.setPush(0, new byte[]{2});
        tx.getInput(0).setWitness(changedWitness);
        model.applyTransaction(tx);

        assertEquals(txId, tx.getTxId());
        assertFalse(model.hasTransactionPublication(model.getTx()));
    }

    @Test
    void handoffIsNotPersistedOrRecreatedByDeserialization() throws Exception {
        PubKeyRing keys = new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
        var model = new BsqSwapProtocolModel(keys);
        model.setTx(new byte[]{1, 2, 3});
        byte[] beforeHandoff = model.toProtoMessage().toByteArray();

        model.recordTransactionPublication(model.getTx());
        var restored = BsqSwapProtocolModel.fromProto(model.toProtoMessage());

        assertArrayEquals(beforeHandoff, model.toProtoMessage().toByteArray());
        assertTrue(model.hasTransactionPublication(model.getTx()));
        assertArrayEquals(model.getTx(), restored.getTx());
        assertFalse(restored.hasTransactionPublication(restored.getTx()));
    }
}
