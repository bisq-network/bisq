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

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.core.account.sign.SignedWitness.VerificationMethod.ARBITRATOR;
import static bisq.core.account.sign.SignedWitness.VerificationMethod.TRADE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class SignedWitnessServiceTest {
    private static final long NOW = Instant.parse("2026-08-14T12:00:00Z").toEpochMilli();

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

        SignedWitness signedWitness = new SignedWitness(ARBITRATOR,
                account1DataHash,
                signature1,
                signer1PubKey,
                witnessOwner1PubKey,
                date1,
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
