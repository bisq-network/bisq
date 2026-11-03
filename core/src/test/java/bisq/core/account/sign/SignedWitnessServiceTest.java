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

package bisq.core.account.sign;


import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.crypto.LowRSigningKey;
import bisq.core.filter.FilterPolicyService;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.security.KeyPair;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static bisq.core.account.sign.SignedWitness.VerificationMethod.ARBITRATOR;
import static bisq.core.account.sign.SignedWitness.VerificationMethod.TRADE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class SignedWitnessServiceTest {
    private static final long NOW = Instant.parse("2026-08-14T12:00:00Z").toEpochMilli();
    // A prefix that is not a valid EC point encoding
    private static final byte[] MALFORMED_EC_KEY = new byte[]{5, 1};

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private SignedWitnessService signedWitnessService;
    private byte[] account1DataHash;
    private byte[] account2DataHash;
    private byte[] account3DataHash;
    private AccountAgeWitness aew1;
    private AccountAgeWitness aew2;
    private AccountAgeWitness aew3;
    private byte[] signature1;
    private byte[] signature2;
    private byte[] signature3;
    private byte[] signer1PubKey;
    private byte[] signer2PubKey;
    private byte[] signer3PubKey;
    private byte[] witnessOwner1PubKey;
    private byte[] witnessOwner2PubKey;
    private byte[] witnessOwner3PubKey;
    private long date1;
    private long date2;
    private long date3;
    private long tradeAmount1;
    private long tradeAmount2;
    private long tradeAmount3;
    private long SIGN_AGE_1 = SignedWitnessService.SIGNER_AGE_DAYS * 3 + 5;
    private long SIGN_AGE_2 = SignedWitnessService.SIGNER_AGE_DAYS * 2 + 4;
    private long SIGN_AGE_3 = SignedWitnessService.SIGNER_AGE_DAYS + 3;
    private KeyRing keyRing;
    private P2PService p2pService;
    private FilterPolicyService filterPolicyService;
    private ECKey arbitrator1Key;
    KeyPair peer1KeyPair;
    KeyPair peer2KeyPair;
    KeyPair peer3KeyPair;

    @BeforeEach
    public void setup() throws Exception {
        AppendOnlyDataStoreService appendOnlyDataStoreService = mock(AppendOnlyDataStoreService.class);
        @SuppressWarnings("deprecation") ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        keyRing = mock(KeyRing.class);
        p2pService = mock(P2PService.class);
        filterPolicyService = mock(FilterPolicyService.class);
        signedWitnessService = new SignedWitnessService(keyRing, p2pService, arbitratorManager, null,
                appendOnlyDataStoreService, filterPolicyService, false, clock);
        account1DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{1});
        account2DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{2});
        account3DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{3});
        long account1CreationTime = getTodayMinusNDays(SIGN_AGE_1 + 1);
        long account2CreationTime = getTodayMinusNDays(SIGN_AGE_2 + 1);
        long account3CreationTime = getTodayMinusNDays(SIGN_AGE_3 + 1);
        aew1 = new AccountAgeWitness(account1DataHash, account1CreationTime);
        aew2 = new AccountAgeWitness(account2DataHash, account2CreationTime);
        aew3 = new AccountAgeWitness(account3DataHash, account3CreationTime);
        arbitrator1Key = LowRSigningKey.from(new ECKey());
        peer1KeyPair = Sig.generateKeyPair();
        peer2KeyPair = Sig.generateKeyPair();
        peer3KeyPair = Sig.generateKeyPair();
        signature1 = arbitrator1Key.signMessage(Utilities.encodeToHex(account1DataHash)).getBytes(StandardCharsets.UTF_8);
        signature2 = Sig.sign(peer1KeyPair.getPrivate(), account2DataHash);
        signature3 = Sig.sign(peer2KeyPair.getPrivate(), account3DataHash);
        date1 = getTodayMinusNDays(SIGN_AGE_1);
        date2 = getTodayMinusNDays(SIGN_AGE_2);
        date3 = getTodayMinusNDays(SIGN_AGE_3);
        signer1PubKey = arbitrator1Key.getPubKey();
        signer2PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        signer3PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());
        witnessOwner1PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        witnessOwner2PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());
        witnessOwner3PubKey = Sig.getPublicKeyBytes(peer3KeyPair.getPublic());
        tradeAmount1 = 1000;
        tradeAmount2 = 1001;
        tradeAmount3 = 1001;
    }

    @Test
    public void testIsValidAccountAgeWitnessOk() {
        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew3));
        assertTrue(signedWitnessService.isSignedByArbitrator(aew1));
    }

    // Anyone can publish a witness for an account hash, which offers show. Its signer key is not checked when the
    // witness is stored.
    @Test
    public void anArbitratorWitnessWithAMalformedSignerKeyIsInvalid() {
        SignedWitness malformed = new SignedWitness(ARBITRATOR, account1DataHash, signature1, MALFORMED_EC_KEY,
                witnessOwner1PubKey, date1, tradeAmount1);
        signedWitnessService.addToMap(malformed);

        assertFalse(signedWitnessService.verifySignature(malformed));
        assertFalse(signedWitnessService.isSignedByArbitrator(aew1));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.getVerifiedWitnessDateList(aew1).isEmpty());
    }

    // The signature of an arbitrator in its canonical encoding, with r = 0, from which bitcoinj cannot recover a key
    @Test
    public void anArbitratorWitnessWithAnUnrecoverableSignatureIsInvalid() {
        byte[] signature = new byte[65];
        signature[0] = 31;
        signature[64] = 1;
        SignedWitness witness = new SignedWitness(ARBITRATOR, account1DataHash,
                java.util.Base64.getEncoder().encode(signature), signer1PubKey, witnessOwner1PubKey, date1,
                tradeAmount1);
        signedWitnessService.addToMap(witness);

        assertFalse(signedWitnessService.verifySignature(witness));
        assertFalse(signedWitnessService.isSignedByArbitrator(aew1));
    }

    @Test
    public void aTradeWitnessWithAMalformedSignerKeyIsInvalid() {
        SignedWitness malformed = new SignedWitness(TRADE, account2DataHash, signature2, new byte[]{1, 2, 3},
                witnessOwner2PubKey, date2, tradeAmount2);
        signedWitnessService.addToMap(malformed);

        assertFalse(signedWitnessService.verifySignature(malformed));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertTrue(signedWitnessService.getVerifiedWitnessDateList(aew2).isEmpty());
    }

    @Test
    public void aMalformedWitnessNextToTheValidOnesOfAnAccountChangesNothing() {
        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey,
                witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey,
                witnessOwner2PubKey, date2, tradeAmount2);
        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(new SignedWitness(ARBITRATOR, account1DataHash, signature1, MALFORMED_EC_KEY,
                witnessOwner1PubKey, date1, tradeAmount1));
        signedWitnessService.addToMap(new SignedWitness(TRADE, account2DataHash, signature2, new byte[]{1, 2, 3},
                witnessOwner2PubKey, date2, tradeAmount2));

        assertTrue(signedWitnessService.isSignedByArbitrator(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertEquals(List.of(date1), signedWitnessService.getVerifiedWitnessDateList(aew1));
        assertEquals(List.of(date2), signedWitnessService.getVerifiedWitnessDateList(aew2));
    }

    // Ways to encode the compact signature of a message that bitcoinj also verifies
    private enum ReEncoding {
        FLIPPED_COMPRESSION_FLAG,
        HIGH_S,
        BASE64_WITH_NEWLINE,
        BASE64_WITH_UNUSED_BITS_SET;

        private byte[] apply(byte[] signatureBase64) {
            byte[] signature = java.util.Base64.getDecoder().decode(signatureBase64);
            switch (this) {
                case FLIPPED_COMPRESSION_FLAG:
                    signature[0] = (byte) ((signature[0] & 0xFF) >= 31 ? signature[0] - 4 : signature[0] + 4);
                    break;
                case HIGH_S:
                    int base = (signature[0] & 0xFF) >= 31 ? 31 : 27;
                    signature[0] = (byte) (base + (((signature[0] & 0xFF) - base) ^ 1));
                    BigInteger s = new BigInteger(1, Arrays.copyOfRange(signature, 33, 65));
                    byte[] highS = ECKey.CURVE.getN().subtract(s).toByteArray();
                    Arrays.fill(signature, 33, 65, (byte) 0);
                    int length = Math.min(highS.length, 32);
                    System.arraycopy(highS, highS.length - length, signature, 65 - length, length);
                    break;
                case BASE64_WITH_NEWLINE:
                    return (java.util.Base64.getEncoder().encodeToString(signature) + "\n")
                            .getBytes(StandardCharsets.UTF_8);
                case BASE64_WITH_UNUSED_BITS_SET:
                    // 65 bytes leave two unused bits in the last character before the padding
                    byte[] encoded = java.util.Base64.getEncoder().encode(signature);
                    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
                    int last = alphabet.indexOf(encoded[encoded.length - 2]);
                    encoded[encoded.length - 2] = (byte) alphabet.charAt(last | 1);
                    return encoded;
            }
            return java.util.Base64.getEncoder().encode(signature);
        }
    }

    // A copy of an arbitrator's witness, re-encoded and with the attacker's key as owner, would make that key a signer
    // root, and an account it signs would count as signed at once
    @ParameterizedTest
    @EnumSource(ReEncoding.class)
    public void aReEncodedCopyOfAnArbitratorWitnessDoesNotMakeItsNewOwnerASigner(ReEncoding reEncoding)
            throws Exception {
        KeyPair attacker = Sig.generateKeyPair();
        byte[] attackerKey = Sig.getPublicKeyBytes(attacker.getPublic());
        byte[] reEncoded = reEncoding.apply(signature1);
        // The re-encoded signature verifies, so only its encoding tells it apart
        arbitrator1Key.verifyMessage(Utilities.encodeToHex(account1DataHash), new String(reEncoded, StandardCharsets.UTF_8));
        SignedWitness copy = new SignedWitness(ARBITRATOR, account1DataHash, reEncoded, signer1PubKey, attackerKey,
                NOW, tradeAmount1);
        SignedWitness signedByTheAttacker = new SignedWitness(TRADE, account3DataHash,
                Sig.sign(attacker.getPrivate(), account3DataHash), attackerKey, witnessOwner3PubKey, NOW, tradeAmount3);
        signedWitnessService.addToMap(copy);
        signedWitnessService.addToMap(signedByTheAttacker);

        assertFalse(signedWitnessService.verifySignature(copy));
        assertFalse(signedWitnessService.isSignedAccountAgeWitness(aew3));
    }

    // In the encoding that bitcoinj creates, a copy with another owner has the hash of the original, so the store
    // ignores it when it has the original
    @Test
    public void aCopyOfAnArbitratorWitnessInItsCanonicalEncodingHasTheHashOfTheOriginal() {
        SignedWitness original = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey,
                witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness copy = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey,
                witnessOwner2PubKey, NOW, tradeAmount1);

        assertTrue(signedWitnessService.verifySignature(original));
        assertArrayEquals(original.getHash(), copy.getHash());
    }

    // The result of the check is cached by the hash of the witness, which does not cover the date, so one service
    // checks both witnesses in both orders
    @Test
    public void aWitnessOfAMainnetArbitratorCountsOnlyIfDatedBeforeArbitrationEnded() {
        long limit = SignedWitnessService.MAINNET_ARBITRATOR_WITNESS_DATE_LIMIT;
        SignedWitness before = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey,
                witnessOwner1PubKey, limit - 1, tradeAmount1);
        SignedWitness after = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey,
                witnessOwner1PubKey, limit, tradeAmount1);
        assertArrayEquals(before.getHash(), after.getHash());

        SignedWitnessService service = serviceWithMainnetArbitratorKeys();
        assertTrue(service.verifySignature(before));
        assertFalse(service.verifySignature(after));
        SignedWitnessService otherService = serviceWithMainnetArbitratorKeys();
        assertFalse(otherService.verifySignature(after));
        assertTrue(otherService.verifySignature(before));
    }

    // The network relays a witness only if it is dated within a day of now, so a copy of a mainnet arbitrator's
    // witness, even in its canonical encoding, cannot make its new owner a signer
    @Test
    public void aRelayedCopyOfAMainnetArbitratorWitnessDoesNotMakeItsNewOwnerASigner() throws Exception {
        SignedWitnessService service = serviceWithMainnetArbitratorKeys();
        KeyPair attacker = Sig.generateKeyPair();
        byte[] attackerKey = Sig.getPublicKeyBytes(attacker.getPublic());
        SignedWitness copy = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, attackerKey,
                NOW, tradeAmount1);
        SignedWitness signedByTheAttacker = new SignedWitness(TRADE, account3DataHash,
                Sig.sign(attacker.getPrivate(), account3DataHash), attackerKey, witnessOwner3PubKey, NOW, tradeAmount3);
        assertTrue(copy.isDateInTolerance(clock));
        service.addToMap(copy);
        service.addToMap(signedByTheAttacker);

        assertFalse(service.verifySignature(copy));
        assertFalse(service.isSignedAccountAgeWitness(aew3));
    }

    // The JDK verifies a trade signature also with an INTEGER without its leading zero, which gives the witness
    // another hash. A copy in that encoding with the attacker's key as owner would be stored next to the original,
    // and the attacker's key would sign as if the signer had signed it.
    @Test
    public void aReEncodedCopyOfATradeWitnessDoesNotMakeItsNewOwnerASigner() throws Exception {
        byte[] signature = signatureWithALeadingZero(peer1KeyPair, account2DataHash);
        byte[] reEncoded = withoutTheFirstLeadingZero(signature);
        // The re-encoded signature verifies, so only its encoding tells it apart
        assertTrue(Sig.verify(peer1KeyPair.getPublic(), account2DataHash, reEncoded));
        KeyPair attacker = Sig.generateKeyPair();
        byte[] attackerKey = Sig.getPublicKeyBytes(attacker.getPublic());
        SignedWitness original = new SignedWitness(TRADE, account2DataHash, signature, signer2PubKey,
                witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness copy = new SignedWitness(TRADE, account2DataHash, reEncoded, signer2PubKey, attackerKey, date2,
                tradeAmount2);
        assertFalse(Arrays.equals(original.getHash(), copy.getHash()));
        assertTrue(copy.verifyHashSize());
        SignedWitness signedByTheAttacker = new SignedWitness(TRADE, account3DataHash,
                Sig.sign(attacker.getPrivate(), account3DataHash), attackerKey, witnessOwner3PubKey, date3,
                tradeAmount3);
        signedWitnessService.addToMap(new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey,
                witnessOwner1PubKey, date1, tradeAmount1));
        signedWitnessService.addToMap(copy);
        signedWitnessService.addToMap(signedByTheAttacker);

        assertTrue(signedWitnessService.verifySignature(original));
        assertFalse(signedWitnessService.verifySignature(copy));
        assertFalse(signedWitnessService.isSignedAccountAgeWitness(aew3));
    }

    // The hash of the witness also covers the signer key, which the JDK also reads in an encoding with a longer
    // length field
    @Test
    public void aCopyOfATradeWitnessWithAReEncodedSignerKeyIsNotValid() throws Exception {
        byte[] reEncodedKey = withALongerLengthField(signer2PubKey);
        // The JDK reads the re-encoded key, and the signature verifies with it
        assertTrue(Sig.verify(Sig.getPublicKeyFromBytes(reEncodedKey), account2DataHash, signature2));
        SignedWitness original = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey,
                witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness copy = new SignedWitness(TRADE, account2DataHash, signature2, reEncodedKey,
                witnessOwner3PubKey, date2, tradeAmount2);
        assertFalse(Arrays.equals(original.getHash(), copy.getHash()));

        assertTrue(signedWitnessService.verifySignature(original));
        assertFalse(signedWitnessService.verifySignature(copy));
    }

    // The X.509 encoding of a DSA key with the length of its outer SEQUENCE in three bytes instead of two
    private static byte[] withALongerLengthField(byte[] key) {
        assertEquals(0x30, key[0] & 0xFF);
        assertEquals(0x82, key[1] & 0xFF);
        byte[] reEncoded = new byte[key.length + 1];
        reEncoded[0] = 0x30;
        reEncoded[1] = (byte) 0x83;
        reEncoded[2] = 0;
        System.arraycopy(key, 2, reEncoded, 3, key.length - 2);
        return reEncoded;
    }

    @Test
    public void onlyTheDerEncodingWhichTheJdkCreatesIsCanonical() throws Exception {
        byte[] signature = signatureWithALeadingZero(peer1KeyPair, account2DataHash);
        assertTrue(SignedWitnessService.isCanonicalDerSignature(signature));
        assertTrue(SignedWitnessService.isCanonicalDerSignature(signature2));

        assertFalse(SignedWitnessService.isCanonicalDerSignature(withoutTheFirstLeadingZero(signature)));
        byte[] withNeedlessZero = new byte[signature2.length + 1];
        withNeedlessZero[0] = 0x30;
        withNeedlessZero[1] = (byte) (signature2[1] + 1);
        withNeedlessZero[2] = 0x02;
        withNeedlessZero[3] = (byte) (signature2[3] + 1);
        System.arraycopy(signature2, 4, withNeedlessZero, 5, signature2.length - 4);
        assertFalse(SignedWitnessService.isCanonicalDerSignature(withNeedlessZero));
        byte[] withTrailingByte = Arrays.copyOf(signature2, signature2.length + 1);
        assertFalse(SignedWitnessService.isCanonicalDerSignature(withTrailingByte));
        byte[] withLongFormLength = new byte[signature2.length + 1];
        withLongFormLength[0] = 0x30;
        withLongFormLength[1] = (byte) 0x81;
        System.arraycopy(signature2, 1, withLongFormLength, 2, signature2.length - 1);
        assertFalse(SignedWitnessService.isCanonicalDerSignature(withLongFormLength));
        assertFalse(SignedWitnessService.isCanonicalDerSignature(new byte[0]));
    }

    // About half of the signatures have an INTEGER with a leading zero, because its first byte would be negative
    private static byte[] signatureWithALeadingZero(KeyPair keyPair, byte[] data) throws Exception {
        for (int i = 0; i < 200; i++) {
            byte[] signature = Sig.sign(keyPair.getPrivate(), data);
            if (withoutTheFirstLeadingZero(signature) != null) {
                return signature;
            }
        }
        throw new IllegalStateException("No signature with a leading zero");
    }

    // The DER SEQUENCE of r and s with the leading zero of the first INTEGER which has one removed, or null
    private static byte[] withoutTheFirstLeadingZero(byte[] signature) {
        int offset = 2;
        for (int integer = 0; integer < 2; integer++) {
            int length = signature[offset + 1];
            if (length > 1 && signature[offset + 2] == 0 && (signature[offset + 3] & 0x80) != 0) {
                byte[] reEncoded = new byte[signature.length - 1];
                System.arraycopy(signature, 0, reEncoded, 0, offset + 2);
                System.arraycopy(signature, offset + 3, reEncoded, offset + 2, signature.length - offset - 3);
                reEncoded[1] = (byte) (signature[1] - 1);
                reEncoded[offset + 1] = (byte) (length - 1);
                return reEncoded;
            }
            offset += 2 + length;
        }
        return null;
    }

    private SignedWitnessService serviceWithMainnetArbitratorKeys() {
        @SuppressWarnings("deprecation") ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        when(arbitratorManager.isLegacyArbitratorPublicKey(any())).thenReturn(true);
        return new SignedWitnessService(keyRing, p2pService, arbitratorManager, null,
                mock(AppendOnlyDataStoreService.class), filterPolicyService, false, clock);
    }

    // A stored witness is indexed again at every start, also one with an empty owner key or account hash
    @Test
    public void aStoredWitnessWithEmptyFieldsDoesNotStopTheStart() {
        SignedWitness valid = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey,
                witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness emptyOwner = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey,
                new byte[0], date2, tradeAmount2);
        SignedWitness emptyAccount = new SignedWitness(TRADE, new byte[0], signature3, signer3PubKey,
                witnessOwner3PubKey, date3, tradeAmount3);
        SignedWitness emptySigner = new SignedWitness(TRADE, account3DataHash, signature3, new byte[0],
                witnessOwner3PubKey, date3, tradeAmount3);
        SignedWitnessStorageService storageService = mock(SignedWitnessStorageService.class);
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> stored = new HashMap<>();
        for (SignedWitness witness : List.of(valid, emptyOwner, emptyAccount, emptySigner)) {
            stored.put(witness.getHashAsByteArray(), witness);
        }
        when(storageService.getMap()).thenReturn(stored);
        when(p2pService.getP2PDataStorage()).thenReturn(mock(P2PDataStorage.class));
        @SuppressWarnings("deprecation") ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        SignedWitnessService service = new SignedWitnessService(keyRing, p2pService, arbitratorManager,
                storageService, mock(AppendOnlyDataStoreService.class), filterPolicyService, false, clock);

        service.onAllServicesInitialized();

        assertEquals(List.of(valid), List.copyOf(service.getSignedWitnessMapValues()));
        assertTrue(service.isSignedByArbitrator(aew1));
        assertEquals(Set.of(valid), service.getRootSignedWitnessSet(true));
    }

    @Test
    public void verifiedDatesExcludeSignatureValidLeavesWithoutACompleteTrustChain() throws Exception {
        SignedWitness trusted = new SignedWitness(ARBITRATOR,
                account1DataHash,
                signature1,
                signer1PubKey,
                witnessOwner1PubKey,
                date1,
                tradeAmount1);
        KeyPair untrustedSigner = Sig.generateKeyPair();
        byte[] untrustedSignature = Sig.sign(untrustedSigner.getPrivate(), account1DataHash);
        SignedWitness untrusted = new SignedWitness(TRADE,
                account1DataHash,
                untrustedSignature,
                Sig.getPublicKeyBytes(untrustedSigner.getPublic()),
                witnessOwner1PubKey,
                date1 - ChronoUnit.DAYS.getDuration().toMillis(),
                tradeAmount1);

        signedWitnessService.addToMap(trusted);
        signedWitnessService.addToMap(untrusted);

        assertTrue(signedWitnessService.verifySignature(untrusted));
        assertEquals(List.of(date1), signedWitnessService.getVerifiedWitnessDateList(aew1));
    }

    @Test
    public void verifiedDatesCanBeBoundToTheProvenWitnessOwner() {
        SignedWitness trusted = new SignedWitness(ARBITRATOR,
                account1DataHash,
                signature1,
                signer1PubKey,
                witnessOwner1PubKey,
                date1,
                tradeAmount1);
        signedWitnessService.addToMap(trusted);

        assertEquals(List.of(date1),
                signedWitnessService.getVerifiedWitnessDateList(aew1, witnessOwner1PubKey));
        assertTrue(signedWitnessService
                .getVerifiedWitnessDateList(aew1, witnessOwner2PubKey)
                .isEmpty());
    }

    @Test
    public void provenOwnerDatesTolerateHistoricalArbitratorOwnerSwap() {
        SignedWitness trusted = new SignedWitness(ARBITRATOR,
                account1DataHash,
                signature1,
                signer1PubKey,
                witnessOwner2PubKey,
                date1,
                tradeAmount1);
        signedWitnessService.addToMap(trusted);

        assertEquals(List.of(date1),
                signedWitnessService.getVerifiedWitnessDateListForProvenOwner(aew1, witnessOwner1PubKey));
    }

    @Test
    public void provenOwnerDatesRejectInvalidArbitratorSignatureDespiteOwnerSwap() {
        SignedWitness forged = new SignedWitness(ARBITRATOR,
                account1DataHash,
                "invalid-arbitrator-signature".getBytes(StandardCharsets.UTF_8),
                signer1PubKey,
                witnessOwner2PubKey,
                date1,
                tradeAmount1);
        signedWitnessService.addToMap(forged);

        assertTrue(signedWitnessService
                .getVerifiedWitnessDateListForProvenOwner(aew1, witnessOwner1PubKey)
                .isEmpty());
    }

    @Test
    public void provenOwnerDatesRejectTradeLeafOwnerMismatch() {
        SignedWitness root = new SignedWitness(ARBITRATOR,
                account1DataHash,
                signature1,
                signer1PubKey,
                witnessOwner1PubKey,
                date1,
                tradeAmount1);
        SignedWitness tradeLeaf = new SignedWitness(TRADE,
                account2DataHash,
                signature2,
                signer2PubKey,
                witnessOwner2PubKey,
                date2,
                tradeAmount2);
        signedWitnessService.addToMap(root);
        signedWitnessService.addToMap(tradeLeaf);

        assertTrue(signedWitnessService
                .getVerifiedWitnessDateListForProvenOwner(aew2, witnessOwner3PubKey)
                .isEmpty());
    }

    @Test
    public void testInvalidArbitratorSignatureDoesNotCountAsSignedByArbitrator() {
        SignedWitness sw1 = new SignedWitness(ARBITRATOR,
                account1DataHash,
                "invalid-arbitrator-signature".getBytes(StandardCharsets.UTF_8),
                signer1PubKey,
                witnessOwner1PubKey,
                date1,
                tradeAmount1);

        signedWitnessService.addToMap(sw1);

        assertFalse(signedWitnessService.verifySignature(sw1));
        assertFalse(signedWitnessService.isSignedByArbitrator(aew1));
    }

    @Test
    public void testArbitratorSignatureBySignerNotInArbitratorListDoesNotCountAsSignedByArbitrator() {
        // A cryptographically valid signature is present, but the signer key is NOT in the
        // arbitrator manager's accepted list. The account must not be treated as arbitrator-signed.
        AppendOnlyDataStoreService appendOnlyDataStoreService = mock(AppendOnlyDataStoreService.class);
        @SuppressWarnings("deprecation") ArbitratorManager strictArbitratorManager = mock(ArbitratorManager.class);
        when(strictArbitratorManager.isPublicKeyInList(any())).thenReturn(false);
        SignedWitnessService strictService = new SignedWitnessService(keyRing, p2pService,
                strictArbitratorManager, null, appendOnlyDataStoreService, filterPolicyService, false, clock);

        SignedWitness sw1 = new SignedWitness(ARBITRATOR,
                account1DataHash,
                signature1,
                signer1PubKey,
                witnessOwner1PubKey,
                date1,
                tradeAmount1);

        strictService.addToMap(sw1);

        assertFalse(strictService.verifySignature(sw1));
        assertFalse(strictService.isSignedByArbitrator(aew1));
    }

    @Test
    public void testLegacyMainnetArbitratorRequiresExplicitOptInWhenRuntimeListRejectsKey() {
        AppendOnlyDataStoreService appendOnlyDataStoreService = mock(AppendOnlyDataStoreService.class);
        ArbitratorManager devArbitratorManager = mock(ArbitratorManager.class);
        when(devArbitratorManager.isPublicKeyInList(any())).thenReturn(false);
        when(devArbitratorManager.isLegacyArbitratorPublicKey(any())).thenReturn(true);
        SignedWitnessService defaultService = new SignedWitnessService(keyRing, p2pService,
                devArbitratorManager, null, appendOnlyDataStoreService, filterPolicyService, false, clock);
        SignedWitnessService mainnetDataService = new SignedWitnessService(keyRing, p2pService,
                devArbitratorManager, null, appendOnlyDataStoreService, filterPolicyService, true, clock);

        // Mainnet arbitrators signed before arbitration ended
        SignedWitness signedWitness = new SignedWitness(ARBITRATOR,
                account1DataHash,
                signature1,
                signer1PubKey,
                witnessOwner1PubKey,
                SignedWitnessService.MAINNET_ARBITRATOR_WITNESS_DATE_LIMIT - 1,
                tradeAmount1);
        defaultService.addToMap(signedWitness);
        mainnetDataService.addToMap(signedWitness);

        assertFalse(defaultService.isSignedByArbitrator(aew1));
        assertFalse(defaultService.isSignedAccountAgeWitness(aew1));
        assertTrue(mainnetDataService.isSignedByArbitrator(aew1));
        assertTrue(mainnetDataService.isSignedAccountAgeWitness(aew1));
    }

    @Test
    public void testValidArbitratorWitnessAlongsideForgedOneStillCountsAsSignedByArbitrator() {
        // A forged arbitrator witness (bad signature) coexists with a valid arbitrator witness
        // for the same account. The valid one must still lift the account to arbitrator-signed.
        ECKey secondArbitratorKey = LowRSigningKey.from(new ECKey());
        byte[] validSignature = secondArbitratorKey.signMessage(Utilities.encodeToHex(account1DataHash))
                .getBytes(StandardCharsets.UTF_8);

        SignedWitness forged = new SignedWitness(ARBITRATOR,
                account1DataHash,
                "invalid-arbitrator-signature".getBytes(StandardCharsets.UTF_8),
                signer1PubKey,
                witnessOwner1PubKey,
                date1,
                tradeAmount1);
        SignedWitness valid = new SignedWitness(ARBITRATOR,
                account1DataHash,
                validSignature,
                secondArbitratorKey.getPubKey(),
                witnessOwner1PubKey,
                date1,
                tradeAmount1);

        signedWitnessService.addToMap(forged);
        signedWitnessService.addToMap(valid);

        assertFalse(signedWitnessService.verifySignature(forged));
        assertTrue(signedWitnessService.verifySignature(valid));
        assertTrue(signedWitnessService.isSignedByArbitrator(aew1));
    }

    @Test
    public void testIsValidAccountAgeWitnessArbitratorSignatureProblem() {
        signature1 = new byte[]{1, 2, 3};

        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }

    @Test
    public void testIsValidAccountAgeWitnessPeerSignatureProblem() {
        signature2 = new byte[]{1, 2, 3};

        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }

    @Test
    public void testInvalidPeerSignatureDoesNotVerify() throws Exception {
        byte[] invalidSignature = Sig.sign(peer1KeyPair.getPrivate(), "wrong-message".getBytes(StandardCharsets.UTF_8));
        SignedWitness signedWitness = new SignedWitness(TRADE,
                account2DataHash,
                invalidSignature,
                signer2PubKey,
                witnessOwner2PubKey,
                date2,
                tradeAmount2);

        assertFalse(signedWitnessService.verifySignature(signedWitness));
    }

    @Test
    public void testPeerSignatureCacheKeepsValidAndInvalidResultsSeparate() throws Exception {
        SignedWitness validSignedWitness = new SignedWitness(TRADE,
                account2DataHash,
                signature2,
                signer2PubKey,
                witnessOwner2PubKey,
                date2,
                tradeAmount2);
        byte[] invalidSignature = Sig.sign(peer1KeyPair.getPrivate(), "wrong-message".getBytes(StandardCharsets.UTF_8));
        SignedWitness invalidSignedWitness = new SignedWitness(TRADE,
                account2DataHash,
                invalidSignature,
                signer2PubKey,
                witnessOwner2PubKey,
                date2,
                tradeAmount2);

        assertTrue(signedWitnessService.verifySignature(validSignedWitness));
        assertTrue(signedWitnessService.verifySignature(validSignedWitness));
        assertFalse(signedWitnessService.verifySignature(invalidSignedWitness));
        assertFalse(signedWitnessService.verifySignature(invalidSignedWitness));
    }

    @Test
    public void testIsValidSelfSignatureOk() throws Exception {
        KeyPair peer1KeyPair = Sig.generateKeyPair();
        signer2PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());

        signature2 = Sig.sign(peer1KeyPair.getPrivate(), account2DataHash);
        signature3 = Sig.sign(peer1KeyPair.getPrivate(), account3DataHash);

        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, signer2PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, signer2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer2PubKey, signer2PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }

    @Test
    public void testIsValidSimpleLoopSignatureProblem() throws Exception {
        // A reasonable case where user1 is signed by user2 and later switches account and the new
        // account gets signed by user2. This is not allowed.
        KeyPair peer1KeyPair = Sig.generateKeyPair();
        KeyPair peer2KeyPair = Sig.generateKeyPair();
        byte[] user1PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        byte[] user2PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());

        signature2 = Sig.sign(peer1KeyPair.getPrivate(), account2DataHash);
        signature3 = Sig.sign(peer2KeyPair.getPrivate(), account3DataHash);

        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, user1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, user1PubKey, user2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, user2PubKey, user1PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }

    @Test
    public void testIsValidAccountAgeWitnessDateTooSoonProblem() {
        date3 = getTodayMinusNDays(SIGN_AGE_2 - 1);

        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }

    @Test
    public void testIsValidAccountAgeWitnessDateTooLateProblem() {
        date3 = getTodayMinusNDays(3);

        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }


    @Test
    public void testIsValidAccountAgeWitnessEndlessLoop() throws Exception {
        byte[] account1DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{1});
        byte[] account2DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{2});
        byte[] account3DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{3});
        long account1CreationTime = getTodayMinusNDays(SIGN_AGE_1 + 1);
        long account2CreationTime = getTodayMinusNDays(SIGN_AGE_2 + 1);
        long account3CreationTime = getTodayMinusNDays(SIGN_AGE_3 + 1);
        AccountAgeWitness aew1 = new AccountAgeWitness(account1DataHash, account1CreationTime);
        AccountAgeWitness aew2 = new AccountAgeWitness(account2DataHash, account2CreationTime);
        AccountAgeWitness aew3 = new AccountAgeWitness(account3DataHash, account3CreationTime);

        KeyPair peer1KeyPair = Sig.generateKeyPair();
        KeyPair peer2KeyPair = Sig.generateKeyPair();
        KeyPair peer3KeyPair = Sig.generateKeyPair();
        byte[] signature1 = Sig.sign(peer3KeyPair.getPrivate(), account1DataHash);
        byte[] signature2 = Sig.sign(peer1KeyPair.getPrivate(), account2DataHash);
        byte[] signature3 = Sig.sign(peer2KeyPair.getPrivate(), account3DataHash);

        byte[] signer1PubKey = Sig.getPublicKeyBytes(peer3KeyPair.getPublic());
        byte[] signer2PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        byte[] signer3PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());
        byte[] witnessOwner1PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        byte[] witnessOwner2PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());
        byte[] witnessOwner3PubKey = Sig.getPublicKeyBytes(peer3KeyPair.getPublic());
        long date1 = getTodayMinusNDays(SIGN_AGE_1);
        long date2 = getTodayMinusNDays(SIGN_AGE_2);
        long date3 = getTodayMinusNDays(SIGN_AGE_3);

        long tradeAmount1 = 1000;
        long tradeAmount2 = 1001;
        long tradeAmount3 = 1001;

        SignedWitness sw1 = new SignedWitness(TRADE, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }


    @Test
    public void testIsValidAccountAgeWitnessLongLoop() throws Exception {
        AccountAgeWitness aew = null;
        KeyPair signerKeyPair;
        KeyPair signedKeyPair = Sig.generateKeyPair();
        int iterations = 1002;
        for (int i = 0; i < iterations; i++) {
            byte[] accountDataHash = org.bitcoinj.core.Utils.sha256hash160(String.valueOf(i).getBytes(StandardCharsets.UTF_8));
            long accountCreationTime = getTodayMinusNDays((iterations - i) * (SignedWitnessService.SIGNER_AGE_DAYS + 1));
            aew = new AccountAgeWitness(accountDataHash, accountCreationTime);
            String accountDataHashAsHexString = Utilities.encodeToHex(accountDataHash);
            byte[] signature;
            byte[] signerPubKey;
            if (i == 0) {
                // use arbitrator key
                ECKey arbitratorKey = LowRSigningKey.from(new ECKey());
                signedKeyPair = Sig.generateKeyPair();
                String signature1String = arbitratorKey.signMessage(accountDataHashAsHexString);
                signature = signature1String.getBytes(StandardCharsets.UTF_8);
                signerPubKey = arbitratorKey.getPubKey();
            } else {
                signerKeyPair = signedKeyPair;
                signedKeyPair = Sig.generateKeyPair();
                signature = Sig.sign(signerKeyPair.getPrivate(), accountDataHash);
                signerPubKey = Sig.getPublicKeyBytes(signerKeyPair.getPublic());
            }
            byte[] witnessOwnerPubKey = Sig.getPublicKeyBytes(signedKeyPair.getPublic());
            long date = getTodayMinusNDays((iterations - i) * (SignedWitnessService.SIGNER_AGE_DAYS + 1));
            SignedWitness sw = new SignedWitness(i == 0 ? ARBITRATOR : TRADE, accountDataHash, signature, signerPubKey, witnessOwnerPubKey, date, tradeAmount1);
            signedWitnessService.addToMap(sw);
        }
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew));
    }


    private long getTodayMinusNDays(long days) {
        return Instant.ofEpochMilli(new Date().getTime()).minus(days, ChronoUnit.DAYS).toEpochMilli();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // publishOwnSignedWitness: the SignedWitness we received from the trading peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We are the buyer (peer3) and the seller (peer1) signed our account2. The seller is signed by an
    // arbitrator, so the seller is a valid signer.
    private final long tradeStartDate = NOW - TimeUnit.DAYS.toMillis(2);
    private final Coin tradeAmount = SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING;

    private void setupTrade() {
        signedWitnessService.addToMap(new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey,
                witnessOwner1PubKey, date1, tradeAmount1));
    }

    // The witness as the seller creates it in signAndPublishAccountAgeWitness, with a chosen date.
    private SignedWitness peersSignedWitness(long date) {
        return new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, witnessOwner3PubKey,
                date, tradeAmount.value);
    }

    private boolean publish(SignedWitness signedWitness) {
        return signedWitnessService.publishOwnSignedWitness(signedWitness, tradeAmount, aew2,
                signer2PubKey, witnessOwner3PubKey, tradeStartDate);
    }

    @Test
    public void publishAcceptsTheWitnessThePeerWasSupposedToCreate() {
        setupTrade();
        SignedWitness signedWitness = peersSignedWitness(NOW - 1000);

        assertTrue(publish(signedWitness));
        assertTrue(signedWitnessService.getSignedWitnessMapValues().contains(signedWitness));
        verify(p2pService, times(1)).addPersistableNetworkPayload(any(PersistableNetworkPayload.class), anyBoolean());
    }

    @Test
    public void publishAcceptsAWitnessFromAMessageDeliveredLate() {
        setupTrade();
        // A message delivered over the mailbox can arrive much later than the seller signed. The date is then
        // far outside the one day tolerance of the P2P layer, but it is inside the duration of the trade.
        SignedWitness signedWitness = peersSignedWitness(tradeStartDate + TimeUnit.HOURS.toMillis(1));

        assertTrue(publish(signedWitness));
        assertTrue(signedWitnessService.getSignedWitnessMapValues().contains(signedWitness));
    }

    @Test
    public void publishRejectsAWitnessDatedBeforeTheTrade() {
        setupTrade();

        assertFalse(publish(peersSignedWitness(tradeStartDate - TimeUnit.DAYS.toMillis(2))));
        assertFalse(publish(peersSignedWitness(Long.MIN_VALUE)));
        assertNothingWasPublished();
    }

    @Test
    public void publishRejectsAForwardDatedWitness() {
        setupTrade();

        assertFalse(publish(peersSignedWitness(NOW + TimeUnit.DAYS.toMillis(2))));
        assertFalse(publish(peersSignedWitness(Long.MAX_VALUE)));
        assertNothingWasPublished();
    }

    @Test
    public void publishRejectsAWitnessIfTheLocalClockIsBehindTheTradeStartDate() {
        setupTrade();
        // If the local clock is behind the stored trade start date by more than twice the tolerance, the lower
        // bound of the accepted range is above the upper bound. We then accept no date at all instead of failing
        // with an exception.
        long tradeStartDateInFuture = NOW + TimeUnit.DAYS.toMillis(3);

        assertFalse(signedWitnessService.publishOwnSignedWitness(peersSignedWitness(NOW - 1000), tradeAmount, aew2,
                signer2PubKey, witnessOwner3PubKey, tradeStartDateInFuture));
        assertNothingWasPublished();
    }

    @Test
    public void publishRejectsAWitnessWhichDoesNotMatchTheTrade() {
        setupTrade();
        long date = NOW - 1000;

        // Not created in a trade
        assertFalse(publish(new SignedWitness(ARBITRATOR, account2DataHash, signature2, signer2PubKey,
                witnessOwner3PubKey, date, tradeAmount.value)));
        // For another account than the one we used in that trade
        assertFalse(publish(new SignedWitness(TRADE, account3DataHash, signature3, signer2PubKey,
                witnessOwner3PubKey, date, tradeAmount.value)));
        // Signed by another key than the key of our trading peer
        assertFalse(publish(new SignedWitness(TRADE, account2DataHash, signature2, signer3PubKey,
                witnessOwner3PubKey, date, tradeAmount.value)));
        // Owned by another key than our own key
        assertFalse(publish(new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey,
                witnessOwner2PubKey, date, tradeAmount.value)));
        // With another trade amount than the one of that trade
        assertFalse(publish(new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey,
                witnessOwner3PubKey, date, tradeAmount.value + 1)));
        assertNothingWasPublished();
    }

    @Test
    public void publishRejectsAWitnessWithAnInvalidSignature() throws Exception {
        setupTrade();
        byte[] invalidSignature = Sig.sign(peer1KeyPair.getPrivate(), "wrong-message".getBytes(StandardCharsets.UTF_8));

        assertFalse(publish(new SignedWitness(TRADE, account2DataHash, invalidSignature, signer2PubKey,
                witnessOwner3PubKey, NOW - 1000, tradeAmount.value)));
        assertNothingWasPublished();
    }

    @Test
    public void publishRejectsAWitnessWhosePeerIsNotAValidSigner() {
        // We do not call setupTrade, so the seller has no arbitrator signed witness and is not a valid signer.
        assertFalse(publish(peersSignedWitness(NOW - 1000)));
        assertNothingWasPublished();
    }

    @Test
    public void publishRejectsAWitnessFromATradeWithATooLowAmount() {
        setupTrade();
        Coin tooLowAmount = SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING.subtract(Coin.SATOSHI);
        SignedWitness signedWitness = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey,
                witnessOwner3PubKey, NOW - 1000, tooLowAmount.value);

        assertFalse(signedWitnessService.publishOwnSignedWitness(signedWitness, tooLowAmount, aew2,
                signer2PubKey, witnessOwner3PubKey, tradeStartDate));
        assertNothingWasPublished();
    }

    private void assertNothingWasPublished() {
        verify(p2pService, never()).addPersistableNetworkPayload(any(PersistableNetworkPayload.class), anyBoolean());
    }

    @Test
    public void testSignAccountAgeWitness_withTooLowTradeAmount() throws CryptoException {
        long accountCreationTime = getTodayMinusNDays(SIGN_AGE_1 + 1);

        KeyPair peerKeyPair = Sig.generateKeyPair();
        KeyPair signerKeyPair = Sig.generateKeyPair();

        when(keyRing.getSignatureKeyPair()).thenReturn(signerKeyPair);

        AccountAgeWitness accountAgeWitness = new AccountAgeWitness(account1DataHash, accountCreationTime);
        signedWitnessService.signAndPublishAccountAgeWitness(Coin.ZERO, accountAgeWitness, peerKeyPair.getPublic());

        verify(p2pService, never()).addPersistableNetworkPayload(any(PersistableNetworkPayload.class), anyBoolean());
    }

    @Test
    public void testSignAccountAgeWitness_withSufficientTradeAmount() throws CryptoException {
        long accountCreationTime = getTodayMinusNDays(SIGN_AGE_1 + 1);

        KeyPair peerKeyPair = Sig.generateKeyPair();
        KeyPair signerKeyPair = Sig.generateKeyPair();

        when(keyRing.getSignatureKeyPair()).thenReturn(signerKeyPair);


        AccountAgeWitness accountAgeWitness = new AccountAgeWitness(account1DataHash, accountCreationTime);
        signedWitnessService.signAndPublishAccountAgeWitness(SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING, accountAgeWitness, peerKeyPair.getPublic());

        verify(p2pService, times(1)).addPersistableNetworkPayload(any(PersistableNetworkPayload.class), anyBoolean());
    }

    /* Signed witness tree
     Each edge in the graph represents one signature

     Arbitrator
      |
     sw1
      |
     sw2
      |
     sw3
    */
    @Test
    public void testBanFilterSingleTree() {
        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(TRADE, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        // Second account is banned, first account is still a signer but the other two are no longer signers
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner2PubKey))).thenReturn(true);
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));

        // First account is banned, no accounts in the tree below it are signers
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner1PubKey))).thenReturn(true);
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner2PubKey))).thenReturn(false);
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }

    /* Signed witness trees
     Each edge in the graph represents one signature

     Arbitrator
      |    |
     sw1  sw2
           |
          sw3
    */
    @Test
    public void testBanFilterTwoTrees() {
        // Signer 2 is signed by arbitrator
        signer2PubKey = arbitrator1Key.getPubKey();
        signature2 = arbitrator1Key.signMessage(Utilities.encodeToHex(account2DataHash)).getBytes(StandardCharsets.UTF_8);

        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(ARBITRATOR, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);

        // Only second account is banned, first account is still a signer but the other two are no longer signers
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner2PubKey))).thenReturn(true);
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));

        // Only first account is banned, account2 and account3 are still signers
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner1PubKey))).thenReturn(true);
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner2PubKey))).thenReturn(false);
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }

    /* Signed witness tree
     Each edge in the graph represents one signature

     Arbitrator
      |    |
     sw1  sw2
      \   /
       sw3
    */
    @Test
    public void testBanFilterJoinedTrees() throws Exception {
        // Signer 2 is signed by arbitrator
        signer2PubKey = arbitrator1Key.getPubKey();
        signature2 = arbitrator1Key.signMessage(Utilities.encodeToHex(account2DataHash)).getBytes(StandardCharsets.UTF_8);

        // Peer1 owns both account1 and account2
//        witnessOwner2PubKey = witnessOwner1PubKey;
//        peer2KeyPair = peer1KeyPair;
//        signature3 = Sig.sign(peer2KeyPair.getPrivate(), account3DataHash);

        // sw1 also signs sw3 (not supported yet but a possible addition for a more robust system)
        var signature3p = Sig.sign(peer1KeyPair.getPrivate(), account3DataHash);
        var signer3pPubKey = witnessOwner1PubKey;
        var date3p = date3;
        var tradeAmount3p = tradeAmount3;

        SignedWitness sw1 = new SignedWitness(ARBITRATOR, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(ARBITRATOR, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(TRADE, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);
        SignedWitness sw3p = new SignedWitness(TRADE, account3DataHash, signature3p, signer3pPubKey, witnessOwner3PubKey, date3p, tradeAmount3p);

        signedWitnessService.addToMap(sw1);
        signedWitnessService.addToMap(sw2);
        signedWitnessService.addToMap(sw3);
        signedWitnessService.addToMap(sw3p);

        // First account is banned, the other two are still signers
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner1PubKey))).thenReturn(true);
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew3));

        // Second account is banned, the other two are still signers
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner1PubKey))).thenReturn(false);
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner2PubKey))).thenReturn(true);
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertTrue(signedWitnessService.isSignerAccountAgeWitness(aew3));

        // First and second account is banned, the third is no longer a signer
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner1PubKey))).thenReturn(true);
        when(filterPolicyService.isWitnessSignerPubKeyBanned(Utilities.bytesAsHexString(witnessOwner2PubKey))).thenReturn(true);
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew1));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew2));
        assertFalse(signedWitnessService.isSignerAccountAgeWitness(aew3));
    }
}
