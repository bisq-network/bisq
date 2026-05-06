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

package bisq.network.p2p.mailbox;

import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.mocks.MockMailboxPayload;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.NetworkEnvelope;

import java.security.KeyPair;
import java.security.PublicKey;

import java.time.Clock;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MailboxMessageServiceTest {
    private static final NodeAddress MY_NODE_ADDRESS = new NodeAddress("my.onion", 9999);
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer.onion", 9999);
    private static final NodeAddress OTHER_SENDER = new NodeAddress("other.onion", 9999);

    @Test
    public void sendEncryptedMailboxMessageFailsBeforeEncryptionWhenPayloadSenderDoesNotMatchLocalNode()
            throws Exception {
        NetworkNode networkNode = mock(NetworkNode.class);
        when(networkNode.getNodeAddress()).thenReturn(MY_NODE_ADDRESS);
        when(networkNode.getAllConnections()).thenReturn(Set.of(mock(Connection.class)));
        EncryptionService encryptionService = mock(EncryptionService.class);
        MailboxMessageService mailboxMessageService = newMailboxMessageService(networkNode, encryptionService);
        mailboxMessageService.onBootstrapped();
        SendMailboxMessageListener listener = mock(SendMailboxMessageListener.class);
        PubKeyRing peersPubKeyRing = mock(PubKeyRing.class);

        mailboxMessageService.sendEncryptedMailboxMessage(PEER_NODE_ADDRESS,
                peersPubKeyRing,
                new MockMailboxPayload("msg", OTHER_SENDER),
                listener);

        verify(listener).onFault("Sender node address of payload is not matching our node address");
        verify(encryptionService, never()).encryptAndSign(any(PubKeyRing.class), any(NetworkEnvelope.class));
        verify(networkNode, never()).sendMessage(any(NodeAddress.class), any(PrefixedSealedAndSignedMessage.class));
    }

    @Test
    public void onAddedRemovesInvalidDecryptedMailboxMessageFromNetwork() throws Exception {
        NetworkNode networkNode = mock(NetworkNode.class);
        when(networkNode.getNodeAddress()).thenReturn(MY_NODE_ADDRESS);
        P2PDataStorage p2PDataStorage = mock(P2PDataStorage.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        KeyRing keyRing = mock(KeyRing.class);
        KeyPair signatureKeyPair = TestUtils.generateKeyPair();
        when(keyRing.getSignatureKeyPair()).thenReturn(signatureKeyPair);
        PublicKey receiversPubKey = TestUtils.generateKeyPair().getPublic();

        ProtectedMailboxStorageEntry protectedMailboxStorageEntry = mock(ProtectedMailboxStorageEntry.class);
        ProtectedMailboxStorageEntry updatedEntry = mock(ProtectedMailboxStorageEntry.class);
        MailboxStoragePayload mailboxStoragePayload = mock(MailboxStoragePayload.class);
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = mock(PrefixedSealedAndSignedMessage.class);
        SealedAndSigned sealedAndSigned = mock(SealedAndSigned.class);

        when(protectedMailboxStorageEntry.getMailboxStoragePayload()).thenReturn(mailboxStoragePayload);
        when(protectedMailboxStorageEntry.getProtectedStoragePayload()).thenReturn(mailboxStoragePayload);
        when(protectedMailboxStorageEntry.getReceiversPubKey()).thenReturn(receiversPubKey);
        when(mailboxStoragePayload.getPrefixedSealedAndSignedMessage()).thenReturn(prefixedSealedAndSignedMessage);
        when(mailboxStoragePayload.serializeForHash()).thenReturn("mailboxPayload".getBytes(StandardCharsets.UTF_8));
        when(prefixedSealedAndSignedMessage.getUid()).thenReturn("uid");
        when(prefixedSealedAndSignedMessage.getSenderNodeAddress()).thenReturn(PEER_NODE_ADDRESS);
        when(prefixedSealedAndSignedMessage.getSealedAndSigned()).thenReturn(sealedAndSigned);
        when(encryptionService.decryptAndVerify(sealedAndSigned)).thenReturn(new DecryptedMessageWithPubKey(
                new MockMailboxPayload("msg", OTHER_SENDER),
                TestUtils.generateKeyPair().getPublic()));
        when(p2PDataStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload,
                signatureKeyPair,
                receiversPubKey)).thenReturn(updatedEntry);
        P2PDataStorage.ByteArray hashOfPayload = P2PDataStorage.get32ByteHashAsByteArray(mailboxStoragePayload);
        Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map = new HashMap<>();
        map.put(hashOfPayload, protectedMailboxStorageEntry);
        when(p2PDataStorage.getMap()).thenReturn(map);
        when(p2PDataStorage.remove(updatedEntry, MY_NODE_ADDRESS)).thenReturn(true);
        MailboxMessageService mailboxMessageService = newMailboxMessageService(networkNode,
                p2PDataStorage,
                encryptionService,
                keyRing);
        mailboxMessageService.onBootstrapped();
        mailboxMessageService.onAllServicesInitialized();

        mailboxMessageService.onAdded(Set.of(protectedMailboxStorageEntry));

        verify(p2PDataStorage).remove(updatedEntry, MY_NODE_ADDRESS);
    }

    @SuppressWarnings("unchecked")
    private static MailboxMessageService newMailboxMessageService(NetworkNode networkNode,
                                                                  EncryptionService encryptionService) {
        KeyRing keyRing = mock(KeyRing.class);
        when(keyRing.getPubKeyRing()).thenReturn(mock(PubKeyRing.class));
        return newMailboxMessageService(networkNode, mock(P2PDataStorage.class), encryptionService, keyRing);
    }

    @SuppressWarnings("unchecked")
    private static MailboxMessageService newMailboxMessageService(NetworkNode networkNode,
                                                                  P2PDataStorage p2PDataStorage,
                                                                  EncryptionService encryptionService,
                                                                  KeyRing keyRing) {
        return new MailboxMessageService(networkNode,
                mock(PeerManager.class),
                p2PDataStorage,
                encryptionService,
                mock(IgnoredMailboxService.class),
                mock(PersistenceManager.class),
                keyRing,
                Clock.systemUTC(),
                false);
    }
}
