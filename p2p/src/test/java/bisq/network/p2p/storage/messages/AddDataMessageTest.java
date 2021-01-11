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

package bisq.network.p2p.storage.messages;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.SealedAndSigned;

import org.apache.commons.lang3.RandomUtils;

import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;

import java.time.Clock;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("UnusedAssignment")
@Slf4j
public class AddDataMessageTest {
    private KeyRing keyRing1;
    private File dir1;


    @Before
    public void setup() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {

        dir1 = File.createTempFile("temp_tests1", "");
        //noinspection ResultOfMethodCallIgnored
        dir1.delete();
        //noinspection ResultOfMethodCallIgnored
        dir1.mkdir();
        keyRing1 = new KeyRing(new KeyStorage(dir1));
    }

    @Test
    public void toProtoBuf() throws Exception {
        SealedAndSigned sealedAndSigned = new SealedAndSigned(RandomUtils.nextBytes(10), RandomUtils.nextBytes(10), RandomUtils.nextBytes(10), keyRing1.getPubKeyRing().getSignaturePubKey());
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(new NodeAddress("host", 1000), sealedAndSigned);
        MailboxStoragePayload mailboxStoragePayload = new MailboxStoragePayload(prefixedSealedAndSignedMessage,
                keyRing1.getPubKeyRing().getSignaturePubKey(), keyRing1.getPubKeyRing().getSignaturePubKey(), MailboxStoragePayload.TTL);
        ProtectedStorageEntry protectedStorageEntry = new ProtectedMailboxStorageEntry(mailboxStoragePayload,
                keyRing1.getSignatureKeyPair().getPublic(), 1, RandomUtils.nextBytes(10), keyRing1.getPubKeyRing().getSignaturePubKey(), Clock.systemDefaultZone());
        AddDataMessage dataMessage1 = new AddDataMessage(protectedStorageEntry);
        protobuf.NetworkEnvelope envelope = dataMessage1.toProtoNetworkEnvelope();

        //TODO Use NetworkProtoResolver, PersistenceProtoResolver or ProtoResolver which are all in io.bisq.common.
      /*  AddDataMessage dataMessage2 = (AddDataMessage) ProtoBufferUtilities.getAddDataMessage(envelope);

        assertTrue(dataMessage1.protectedStorageEntry.getStoragePayload().equals(dataMessage2.protectedStorageEntry.getStoragePayload()));
        assertTrue(dataMessage1.protectedStorageEntry.equals(dataMessage2.protectedStorageEntry));
        assertTrue(dataMessage1.equals(dataMessage2));*/
    }

}
