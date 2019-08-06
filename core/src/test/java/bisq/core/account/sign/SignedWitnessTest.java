package bisq.core.account.sign;

import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import com.google.common.base.Charsets;

import java.time.Instant;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SignedWitnessTest {
    @Test
    public void testProtoRoundTrip() {
        ECKey arbitrator1Key = new ECKey();

        byte[] witnessOwner1PubKey = Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic());

        byte[] witnessHash = Utils.sha256hash160(new byte[]{1});
        byte[] witnessHashSignature = arbitrator1Key.signMessage(Utilities.encodeToHex(witnessHash)).getBytes(Charsets.UTF_8);
        SignedWitness signedWitness = new SignedWitness(true, witnessHash, witnessHashSignature, arbitrator1Key.getPubKey(), witnessOwner1PubKey, Instant.now().getEpochSecond(), 100);
        assertEquals(signedWitness, SignedWitness.fromProto(signedWitness.toProtoMessage().getSignedWitness()));

    }

}
