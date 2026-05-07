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

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Base64;

import java.security.KeyPair;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.ValidationTestUtils.pubKeyRing;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DsaSignatureValidationTest {
    static final byte[] ACCOUNT_AGE_WITNESS_NONCE =
            "account-age-witness-nonce".getBytes(StandardCharsets.UTF_8);

    /* --------------------------------------------------------------------- */
    // DSA signature
    /* --------------------------------------------------------------------- */

    @Test
    void checkAccountAgeWitnessSignatureAcceptsSignatureOfNonce() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = pubKeyRing(signatureKeyPair);
        byte[] accountAgeWitnessSignature = Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE);

        assertSame(accountAgeWitnessSignature, DsaSignatureValidation.checkDSASignature(
                accountAgeWitnessSignature,
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing.getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsSignatureOfDifferentNonce() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = pubKeyRing(signatureKeyPair);
        byte[] accountAgeWitnessSignature = Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE);
        byte[] otherNonce = "other-nonce".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> DsaSignatureValidation.checkDSASignature(
                accountAgeWitnessSignature,
                otherNonce,
                pubKeyRing.getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsSignatureFromDifferentPubKey() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = pubKeyRing(Sig.generateKeyPair());
        byte[] accountAgeWitnessSignature = Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE);

        assertThrows(IllegalArgumentException.class, () -> DsaSignatureValidation.checkDSASignature(
                accountAgeWitnessSignature,
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing.getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullSignature() {
        assertThrows(NullPointerException.class, () -> DsaSignatureValidation.checkDSASignature(null,
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing(Sig.generateKeyPair()).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsEmptySignature() {
        assertThrows(IllegalArgumentException.class, () -> DsaSignatureValidation.checkDSASignature(new byte[0],
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing(Sig.generateKeyPair()).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullNonce() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();

        assertThrows(NullPointerException.class, () -> DsaSignatureValidation.checkDSASignature(
                Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE),
                null,
                pubKeyRing(signatureKeyPair).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullPubKeyRing() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();

        assertThrows(NullPointerException.class, () -> DsaSignatureValidation.checkDSASignature(
                Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE),
                ACCOUNT_AGE_WITNESS_NONCE,
                null));
    }

    /* --------------------------------------------------------------------- */
    // Base64 DSA signature
    /* --------------------------------------------------------------------- */

    @Test
    void checkBase64SignatureAcceptsBase64EncodedDSASignature() {
        byte[] signature = new byte[]{1, 2, 3};
        String signatureBase64 = Base64.encode(signature);

        assertEquals(signatureBase64, DsaSignatureValidation.checkBase64DSASignature(signatureBase64));
        assertArrayEquals(signature, DsaSignatureValidation.fromBase64DSASignature(signatureBase64));
    }

    @Test
    void checkBase64SignatureRejectsInvalidBase64EncodedDSASignature() {
        assertThrows(IllegalArgumentException.class, () -> DsaSignatureValidation.checkBase64DSASignature("not base64"));
    }

    @Test
    void checkBase64SignatureRejectsNullDSASignature() {
        assertThrows(NullPointerException.class, () -> DsaSignatureValidation.checkBase64DSASignature(null));
    }

    @Test
    void checkBase64SignatureRejectsBlankDSASignature() {
        assertThrows(IllegalArgumentException.class, () -> DsaSignatureValidation.checkBase64DSASignature(" "));
    }

}
