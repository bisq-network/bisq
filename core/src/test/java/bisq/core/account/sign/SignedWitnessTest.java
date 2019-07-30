package bisq.core.account.sign;

import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import com.google.common.base.Charsets;

import java.security.KeyPair;

import java.time.Instant;

import org.junit.Test;

import static org.junit.Assert.assertEquals;



import protobuf.PersistableNetworkPayload;

public class SignedWitnessTest {
    @Test
    public void testProtoRoundTrip() {
        ECKey arbitrator1Key = new ECKey();

        KeyPair peer1KeyPair = Sig.generateKeyPair();
        byte[] witnessOwner1PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());

        byte[] witnessHash = Utils.sha256hash160(new byte[]{1});
        byte[] signature1 = arbitrator1Key.signMessage(Utilities.encodeToHex(witnessHash)).getBytes(Charsets.UTF_8);
        SignedWitness signedWitness = new SignedWitness(true, witnessHash, signature1, arbitrator1Key.getPubKey(), witnessOwner1PubKey, Instant.now().getEpochSecond(), 100);
        PersistableNetworkPayload proto = signedWitness.toProtoMessage();
        assertEquals(signedWitness, SignedWitness.fromProto(proto.getSignedWitness()));

    }

}
