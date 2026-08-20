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

package bisq.core.trade.protocol.bisq_v1.model;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.TradeManager;
import bisq.core.trade.protocol.Provider;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Transaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessModelBuyerPaymentAccountValidationTest {
    @Test
    void validationStateSurvivesPersistence() {
        ProcessModel processModel = new ProcessModel("offer-id", "account-id", pubKeyRing());
        processModel.setBuyerPaymentAccountValidated(true);
        processModel.setDepositTxAndDelayedPayoutTxMessageDelivered(true);

        ProcessModel restored = ProcessModel.fromProto(processModel.toProtoMessage(), mock(CoreProtoResolver.class));

        assertTrue(restored.isBuyerPaymentAccountValidated());
        assertTrue(restored.isDepositTxAndDelayedPayoutTxMessageDelivered());
    }

    @Test
    void finalizedDepositTransactionIsRestoredForDeferredPublication() {
        ProcessModel processModel = new ProcessModel("offer-id", "account-id", pubKeyRing());
        Transaction finalizedDepositTx = mock(Transaction.class);
        byte[] serializedTransaction = {1, 2, 3};
        when(finalizedDepositTx.bitcoinSerialize()).thenReturn(serializedTransaction);
        processModel.setFinalizedDepositTx(finalizedDepositTx);

        protobuf.ProcessModel proto = processModel.toProtoMessage();
        ProcessModel restored = ProcessModel.fromProto(proto, mock(CoreProtoResolver.class));
        Provider provider = mock(Provider.class);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        Transaction parsedTransaction = mock(Transaction.class);
        when(provider.getBtcWalletService()).thenReturn(btcWalletService);
        when(btcWalletService.getTxFromSerializedTx(serializedTransaction)).thenReturn(parsedTransaction);
        restored.applyTransient(provider, mock(TradeManager.class), null);

        assertArrayEquals(serializedTransaction, proto.getFinalizedDepositTx().toByteArray());
        assertSame(parsedTransaction, restored.getDepositTx());
    }

    @Test
    void oldPersistenceWithoutValidationStateDefaultsToUnvalidated() {
        assertFalse(protobuf.ProcessModel.newBuilder().build().getBuyerPaymentAccountValidated());
        assertFalse(protobuf.ProcessModel.newBuilder().build().getDepositTxAndDelayedPayoutTxMessageDelivered());
        assertTrue(protobuf.ProcessModel.newBuilder().build().getFinalizedDepositTx().isEmpty());
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
    }
}
