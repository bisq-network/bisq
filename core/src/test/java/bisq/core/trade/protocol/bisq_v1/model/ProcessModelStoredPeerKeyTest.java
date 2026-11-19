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

import bisq.core.proto.CoreProtoResolver;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.spec.DSAPublicKeySpec;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ProcessModelStoredPeerKeyTest {

    // Makers up to v1.10.11 stored the key ring of a take offer request with any DSA key which the JDK reads, and
    // one trade that cannot be read makes the whole trade file unreadable. A trade with a peer key above the bounds
    // for a verification must still load. The key is refused when a signature is verified with it.
    @Test
    void peerKeyAboveTheVerificationBoundsStillLoads() throws Exception {
        PubKeyRing ownPubKeyRing = new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
        SecureRandom random = new SecureRandom();
        BigInteger p = new BigInteger(3072, random).setBit(3071).setBit(0);
        BigInteger q = BigInteger.probablePrime(160, random);
        byte[] peerSigKey = KeyFactory.getInstance(Sig.KEY_ALGO)
                .generatePublic(new DSAPublicKeySpec(BigInteger.ONE, p, q, BigInteger.TWO)).getEncoded();
        PubKeyRing peerPubKeyRing = new PubKeyRing(peerSigKey, ownPubKeyRing.getEncryptionPubKeyBytes());
        ProcessModel processModel = new ProcessModel("offer-id", "account-id", ownPubKeyRing);
        processModel.getTradePeer().setPubKeyRing(peerPubKeyRing);

        ProcessModel restored = ProcessModel.fromProto(processModel.toProtoMessage(), mock(CoreProtoResolver.class));

        PubKeyRing restoredPeerPubKeyRing = restored.getTradePeer().getPubKeyRing();
        assertEquals(peerPubKeyRing, restoredPeerPubKeyRing);
        assertThrows(CryptoException.class,
                () -> Sig.verify(restoredPeerPubKeyRing.getSignaturePubKey(), new byte[32], new byte[46]));
    }
}
