package bisq.core.account.sign;

import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.nio.charset.StandardCharsets;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.core.account.sign.SignedWitness.VerificationMethod.ARBITRATOR;
import static bisq.core.account.sign.SignedWitness.VerificationMethod.TRADE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SignedWitnessTest {

    private ECKey arbitrator1Key;
    private byte[] witnessOwner1PubKey;
    private byte[] witnessHash;
    private byte[] witnessHashSignature;

    @BeforeEach
    public void setUp() {
        arbitrator1Key = new ECKey();
        witnessOwner1PubKey = Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic());
        witnessHash = Utils.sha256hash160(new byte[]{1});
        witnessHashSignature = arbitrator1Key.signMessage(Utilities.encodeToHex(witnessHash)).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void testProtoRoundTrip() {
        SignedWitness signedWitness = new SignedWitness(ARBITRATOR, witnessHash, witnessHashSignature, arbitrator1Key.getPubKey(), witnessOwner1PubKey, Instant.now().getEpochSecond(), 100);
        assertEquals(signedWitness, SignedWitness.fromProto(signedWitness.toProtoMessage().getSignedWitness()));
    }

    @Test
    public void isImmutable() {
        byte[] signerPubkey = arbitrator1Key.getPubKey();
        SignedWitness signedWitness = new SignedWitness(TRADE, witnessHash, witnessHashSignature, signerPubkey, witnessOwner1PubKey, Instant.now().getEpochSecond(), 100);
        byte[] originalWitnessHash = signedWitness.getAccountAgeWitnessHash().clone();
        witnessHash[0] += 1;
        assertArrayEquals(originalWitnessHash, signedWitness.getAccountAgeWitnessHash());

        byte[] originalWitnessHashSignature = signedWitness.getSignature().clone();
        witnessHashSignature[0] += 1;
        assertArrayEquals(originalWitnessHashSignature, signedWitness.getSignature());

        byte[] originalSignerPubkey = signedWitness.getSignerPubKey().clone();
        signerPubkey[0] += 1;
        assertArrayEquals(originalSignerPubkey, signedWitness.getSignerPubKey());
        byte[] originalwitnessOwner1PubKey = signedWitness.getWitnessOwnerPubKey().clone();
        witnessOwner1PubKey[0] += 1;
        assertArrayEquals(originalwitnessOwner1PubKey, signedWitness.getWitnessOwnerPubKey());
    }

    @Test
    public void isDateInToleranceAcceptsDatesUpToOneDayOff() {
        long now = Instant.parse("2026-08-14T12:00:00Z").toEpochMilli();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC);
        long oneDay = TimeUnit.DAYS.toMillis(1);

        assertTrue(signedWitnessWithDate(now).isDateInTolerance(clock));
        assertTrue(signedWitnessWithDate(now - oneDay).isDateInTolerance(clock));
        assertTrue(signedWitnessWithDate(now + oneDay).isDateInTolerance(clock));
    }

    @Test
    public void isDateInToleranceRejectsDatesMoreThanOneDayOff() {
        long now = Instant.parse("2026-08-14T12:00:00Z").toEpochMilli();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC);
        long oneDay = TimeUnit.DAYS.toMillis(1);

        assertFalse(signedWitnessWithDate(now - oneDay - 1).isDateInTolerance(clock));
        assertFalse(signedWitnessWithDate(now + oneDay + 1).isDateInTolerance(clock));
    }

    @Test
    public void isDateInToleranceRejectsExtremeDates() {
        long now = Instant.parse("2026-08-14T12:00:00Z").toEpochMilli();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC);

        assertFalse(signedWitnessWithDate(Long.MIN_VALUE).isDateInTolerance(clock));
        assertFalse(signedWitnessWithDate(Long.MAX_VALUE).isDateInTolerance(clock));
        // This date makes (now - date) overflow to Long.MIN_VALUE. The previous implementation
        // used Math.abs(now - date), which stays negative in that case and accepted the payload.
        assertFalse(signedWitnessWithDate(now + Long.MIN_VALUE).isDateInTolerance(clock));
    }

    private SignedWitness signedWitnessWithDate(long date) {
        return new SignedWitness(TRADE, witnessHash, witnessHashSignature, arbitrator1Key.getPubKey(),
                witnessOwner1PubKey, date, 100);
    }

}
