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

package bisq.core.trade.validation;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Base64;

import java.security.KeyPair;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.TradeValidationTestUtils.pubKeyRing;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TradeValidationTest {
    static final byte[] ACCOUNT_AGE_WITNESS_NONCE =
            "account-age-witness-nonce".getBytes(StandardCharsets.UTF_8);


    @Test
    void checkTradeIdAcceptsMatchingTradeMessageTradeId() {
        String tradeId = "trade-id";

        assertSame(tradeId, TradeValidation.checkTradeId(tradeId, TradeValidationTestUtils.tradeMessage(tradeId)));
    }

    @Test
    void checkTradeIdRejectsMismatchingTradeMessageTradeId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkTradeId("trade-id", TradeValidationTestUtils.tradeMessage("other-trade-id")));

        assertEquals("TradeId trade-id is not valid", exception.getMessage());
    }

    @Test
    void checkTradeIdRejectsNullTradeId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTradeId(null, TradeValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidReturnsTrueForMatchingTradeMessageTradeId() {
        assertEquals(true, TradeValidation.isTradeIdValid("trade-id", TradeValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidReturnsFalseForMismatchingTradeMessageTradeId() {
        assertEquals(false, TradeValidation.isTradeIdValid("trade-id", TradeValidationTestUtils.tradeMessage("other-trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.isTradeIdValid(null, TradeValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeMessage() {
        assertThrows(NullPointerException.class, () -> TradeValidation.isTradeIdValid("trade-id", null));
    }



    @Test
    void checkAccountAgeWitnessSignatureAcceptsSignatureOfNonce() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = pubKeyRing(signatureKeyPair);
        byte[] accountAgeWitnessSignature = Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE);

        assertSame(accountAgeWitnessSignature, TradeValidation.checkDSASignature(
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

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkDSASignature(
                accountAgeWitnessSignature,
                otherNonce,
                pubKeyRing.getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsSignatureFromDifferentPubKey() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = pubKeyRing(Sig.generateKeyPair());
        byte[] accountAgeWitnessSignature = Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE);

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkDSASignature(
                accountAgeWitnessSignature,
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing.getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullSignature() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkDSASignature(null,
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing(Sig.generateKeyPair()).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsEmptySignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkDSASignature(new byte[0],
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing(Sig.generateKeyPair()).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullNonce() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();

        assertThrows(NullPointerException.class, () -> TradeValidation.checkDSASignature(
                Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE),
                null,
                pubKeyRing(signatureKeyPair).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullPubKeyRing() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();

        assertThrows(NullPointerException.class, () -> TradeValidation.checkDSASignature(
                Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE),
                ACCOUNT_AGE_WITNESS_NONCE,
                null));
    }

    @Test
    void checkBase64SignatureAcceptsBase64EncodedDSASignature() {
        byte[] signature = new byte[]{1, 2, 3};
        String signatureBase64 = Base64.encode(signature);

        assertEquals(signatureBase64, TradeValidation.checkBase64DSASignature(signatureBase64));
        assertArrayEquals(signature, TradeValidation.fromBase64DSASignature(signatureBase64));
    }

    @Test
    void checkBase64SignatureRejectsInvalidBase64EncodedDSASignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBase64DSASignature("not base64"));
    }

    @Test
    void checkBase64SignatureRejectsNullDSASignature() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkBase64DSASignature(null));
    }

    @Test
    void checkBase64SignatureRejectsBlankDSASignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBase64DSASignature(" "));
    }

    @Test
    void checkPeersDateAcceptsDateWithinAllowedRange() {
        long now = System.currentTimeMillis();

        assertEquals(now, TradeValidation.checkPeersDate(now));
        assertEquals(now - TradeValidation.MAX_DATE_DEVIATION + 60_000,
                TradeValidation.checkPeersDate(now - TradeValidation.MAX_DATE_DEVIATION + 60_000));
        assertEquals(now + TradeValidation.MAX_DATE_DEVIATION - 60_000,
                TradeValidation.checkPeersDate(now + TradeValidation.MAX_DATE_DEVIATION - 60_000));
    }

    @Test
    void checkPeersDateRejectsDateOlderThanAllowedRange() {
        long currentDate = System.currentTimeMillis() - TradeValidation.MAX_DATE_DEVIATION - 60_000;

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(currentDate));
    }

    @Test
    void checkPeersDateRejectsDateNewerThanAllowedRange() {
        long currentDate = System.currentTimeMillis() + TradeValidation.MAX_DATE_DEVIATION + 60_000;

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(currentDate));
    }

    @Test
    void checkPeersDateRejectsZeroDate() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(0));
    }


    @Test
    void checkByteArrayWithExpectedAcceptsMatchingByteArrays() {
        byte[] current = new byte[]{1, 2, 3};

        assertSame(current, TradeValidation.checkHashFromContract(current, new byte[]{1, 2, 3}));
    }

    @Test
    void checkByteArrayWithExpectedRejectsMismatchingByteArrays() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));

        assertEquals("current is not matching expected. current=010203, expected=010204", exception.getMessage());
    }

    @Test
    void checkByteArrayWithExpectedRejectsNullAndEmptyByteArrays() {
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkHashFromContract(null, new byte[]{1}));
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1}, null));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[0], new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1}, new byte[0]));
    }
}
