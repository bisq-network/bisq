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

package bisq.core.account.witness;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Optional;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAgeWitnessUtilsTest {
    @Test
    void accountAgeExportRejectsBannedOwnerBeforeReadingTheAccountPreimage() {
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        PaymentAccount account = mock(PaymentAccount.class);
        PaymentAccountPayload paymentAccountPayload = mock(PaymentAccountPayload.class);
        KeyRing keyRing = mock(KeyRing.class);
        PubKeyRing pubKeyRing = mock(PubKeyRing.class);
        KeyPair ownerKeyPair = Sig.generateKeyPair();
        AccountAgeWitness witness = new AccountAgeWitness(new byte[20], System.currentTimeMillis());
        when(account.getPaymentAccountPayload()).thenReturn(paymentAccountPayload);
        when(keyRing.getPubKeyRing()).thenReturn(pubKeyRing);
        when(keyRing.getSignatureKeyPair()).thenReturn(ownerKeyPair);
        when(accountAgeWitnessService.findWitness(paymentAccountPayload, pubKeyRing))
                .thenReturn(Optional.of(witness));
        byte[] ownerPublicKey = Sig.getPublicKeyBytes(ownerKeyPair.getPublic());
        when(accountAgeWitnessService.isWitnessOwnerPubKeyBanned(aryEq(ownerPublicKey))).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> AccountAgeWitnessUtils.signAccountAgeAndBisq2ProfileId(
                        accountAgeWitnessService,
                        account,
                        keyRing,
                        "12".repeat(20)));

        verify(accountAgeWitnessService).isWitnessOwnerPubKeyBanned(aryEq(ownerPublicKey));
        verify(accountAgeWitnessService, never()).getAccountInputDataWithSalt(any());
    }
}
