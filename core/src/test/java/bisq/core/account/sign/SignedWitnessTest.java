package bisq.core.account.sign;

import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import com.google.common.base.Charsets;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;

import static bisq.core.account.sign.SignedWitness.VerificationMethod.ARBITRATOR;
import static bisq.core.account.sign.SignedWitness.VerificationMethod.TRADE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SignedWitnessTest {

    private ECKey arbitrator1Key;
    private byte[] witnessOwner1PubKey;
    private byte[] witnessHash;
    private byte[] witnessHashSignature;

    @Before
    public void setUp() {
        arbitrator1Key = new ECKey();
        witnessOwner1PubKey = Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic());
        witnessHash = Utils.sha256hash160(new byte[]{1});
        witnessHashSignature = arbitrator1Key.signMessage(Utilities.encodeToHex(witnessHash)).getBytes(Charsets.UTF_8);
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

}
