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

package bisq.network.p2p;

import bisq.network.Socks5ProxyProvider;
import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.mocks.MockMailboxPayload;
import bisq.network.p2p.mocks.MockPayload;
import bisq.network.p2p.mocks.MockSignaturePubKeyAwareMailboxPayload;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.RequestDataManager;
import bisq.network.p2p.peers.keepalive.KeepAliveManager;
import bisq.network.p2p.peers.peerexchange.PeerExchangeManager;
import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.proto.network.NetworkEnvelope;

import java.security.KeyPair;
import java.security.PublicKey;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class P2PServiceTest {
    private static final NodeAddress MY_NODE_ADDRESS = new NodeAddress("my.onion", 9999);
    private static final NodeAddress ENVELOPE_SENDER = new NodeAddress("sender.onion", 9999);
    private static final NodeAddress OTHER_SENDER = new NodeAddress("other.onion", 9999);

    @Test
    public void onMessageDispatchesDirectMessageWhenPayloadEnvelopeAndConnectionSendersMatch() throws Exception {
        DirectReceiveFixture fixture = new DirectReceiveFixture(
                new MockMailboxPayload("msg", ENVELOPE_SENDER),
                ENVELOPE_SENDER,
                ENVELOPE_SENDER);
        AtomicReference<DecryptedMessageWithPubKey> receivedMessage = new AtomicReference<>();
        AtomicReference<NodeAddress> receivedSender = new AtomicReference<>();
        fixture.p2PService.addDecryptedDirectMessageListener((decryptedMessageWithPubKey, senderNodeAddress) -> {
            receivedMessage.set(decryptedMessageWithPubKey);
            receivedSender.set(senderNodeAddress);
        });

        fixture.p2PService.onMessage(fixture.sealedMessage, fixture.connection);

        assertSame(fixture.decryptedMessageWithPubKey, receivedMessage.get());
        assertEquals(ENVELOPE_SENDER, receivedSender.get());
        verify(fixture.connection).maybeHandleSupportedCapabilitiesMessage(same(fixture.payload));
    }

    @Test
    public void onMessageDropsDirectMessageWhenPayloadSenderDoesNotMatchOuterEnvelopeSender() throws Exception {
        DirectReceiveFixture fixture = new DirectReceiveFixture(
                new MockMailboxPayload("msg", OTHER_SENDER),
                ENVELOPE_SENDER,
                ENVELOPE_SENDER);
        AtomicBoolean listenerCalled = new AtomicBoolean();
        fixture.p2PService.addDecryptedDirectMessageListener((decryptedMessageWithPubKey, senderNodeAddress) ->
                listenerCalled.set(true));

        fixture.p2PService.onMessage(fixture.sealedMessage, fixture.connection);

        assertFalse(listenerCalled.get());
        verify(fixture.connection, never()).maybeHandleSupportedCapabilitiesMessage(any(NetworkEnvelope.class));
    }

    @Test
    public void onMessageDropsDirectMessageWhenConnectionSenderDoesNotMatchOuterEnvelopeSender() throws Exception {
        DirectReceiveFixture fixture = new DirectReceiveFixture(
                new MockMailboxPayload("msg", ENVELOPE_SENDER),
                ENVELOPE_SENDER,
                OTHER_SENDER);
        AtomicBoolean listenerCalled = new AtomicBoolean();
        fixture.p2PService.addDecryptedDirectMessageListener((decryptedMessageWithPubKey, senderNodeAddress) ->
                listenerCalled.set(true));

        fixture.p2PService.onMessage(fixture.sealedMessage, fixture.connection);

        assertFalse(listenerCalled.get());
        verify(fixture.connection, never()).maybeHandleSupportedCapabilitiesMessage(any(NetworkEnvelope.class));
    }

    @Test
    public void onMessageDropsDirectMessageWhenPayloadSenderSignaturePubKeyDoesNotMatchSealedPayload()
            throws Exception {
        PublicKey payloadSenderSignaturePubKey = TestUtils.generateKeyPair().getPublic();
        PublicKey sealedPayloadSignaturePubKey = TestUtils.generateKeyPair().getPublic();
        DirectReceiveFixture fixture = new DirectReceiveFixture(
                new MockSignaturePubKeyAwareMailboxPayload("msg", ENVELOPE_SENDER, payloadSenderSignaturePubKey),
                sealedPayloadSignaturePubKey,
                ENVELOPE_SENDER,
                ENVELOPE_SENDER);
        AtomicBoolean listenerCalled = new AtomicBoolean();
        fixture.p2PService.addDecryptedDirectMessageListener((decryptedMessageWithPubKey, senderNodeAddress) ->
                listenerCalled.set(true));

        fixture.p2PService.onMessage(fixture.sealedMessage, fixture.connection);

        assertFalse(listenerCalled.get());
        verify(fixture.connection, never()).maybeHandleSupportedCapabilitiesMessage(any(NetworkEnvelope.class));
    }

    @Test
    public void onMessageDispatchesDirectMessageWithoutPayloadSenderWhenOuterEnvelopeAndConnectionMatch()
            throws Exception {
        DirectReceiveFixture fixture = new DirectReceiveFixture(
                new MockPayload("msg"),
                ENVELOPE_SENDER,
                ENVELOPE_SENDER);
        AtomicReference<NodeAddress> receivedSender = new AtomicReference<>();
        fixture.p2PService.addDecryptedDirectMessageListener((decryptedMessageWithPubKey, senderNodeAddress) ->
                receivedSender.set(senderNodeAddress));

        fixture.p2PService.onMessage(fixture.sealedMessage, fixture.connection);

        assertEquals(ENVELOPE_SENDER, receivedSender.get());
        verify(fixture.connection).maybeHandleSupportedCapabilitiesMessage(same(fixture.payload));
    }

    @Test
    public void sendEncryptedDirectMessageFailsBeforeEncryptionWhenPayloadSenderDoesNotMatchLocalNode()
            throws Exception {
        DirectSendFixture fixture = new DirectSendFixture();
        setBootstrapped(fixture.p2PService);
        SendDirectMessageListener listener = mock(SendDirectMessageListener.class);

        fixture.p2PService.sendEncryptedDirectMessage(ENVELOPE_SENDER,
                mock(PubKeyRing.class),
                new MockMailboxPayload("msg", OTHER_SENDER),
                listener);

        verify(listener).onFault("Sender node address of payload is not matching our node address");
        verify(fixture.encryptionService, never()).encryptAndSign(any(PubKeyRing.class), any(NetworkEnvelope.class));
        verify(fixture.networkNode, never()).sendMessage(any(NodeAddress.class), any(NetworkEnvelope.class));
    }

    @Test
    public void sendEncryptedDirectMessageFailsBeforeEncryptionWhenPayloadSenderSignaturePubKeyDoesNotMatchLocalKey()
            throws Exception {
        KeyPair localSignatureKeyPair = TestUtils.generateKeyPair();
        DirectSendFixture fixture = new DirectSendFixture(localSignatureKeyPair);
        setBootstrapped(fixture.p2PService);
        SendDirectMessageListener listener = mock(SendDirectMessageListener.class);

        fixture.p2PService.sendEncryptedDirectMessage(ENVELOPE_SENDER,
                mock(PubKeyRing.class),
                new MockSignaturePubKeyAwareMailboxPayload("msg",
                        MY_NODE_ADDRESS,
                        TestUtils.generateKeyPair().getPublic()),
                listener);

        verify(listener).onFault("Sender signature pubkey of payload is not matching our signature pubkey");
        verify(fixture.encryptionService, never()).encryptAndSign(any(PubKeyRing.class), any(NetworkEnvelope.class));
        verify(fixture.networkNode, never()).sendMessage(any(NodeAddress.class), any(NetworkEnvelope.class));
    }

    private static void setBootstrapped(P2PService p2PService) throws ReflectiveOperationException {
        Field field = P2PService.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true);
        field.setBoolean(p2PService, true);
    }

    private static P2PService newP2PService(NetworkNode networkNode, EncryptionService encryptionService) {
        return newP2PService(networkNode, encryptionService, mock(KeyRing.class));
    }

    private static P2PService newP2PService(NetworkNode networkNode,
                                            EncryptionService encryptionService,
                                            KeyRing keyRing) {
        return new P2PService(networkNode,
                mock(PeerManager.class),
                mock(P2PDataStorage.class),
                mock(RequestDataManager.class),
                mock(PeerExchangeManager.class),
                mock(KeepAliveManager.class),
                mock(Broadcaster.class),
                mock(Socks5ProxyProvider.class),
                encryptionService,
                keyRing,
                mock(MailboxMessageService.class));
    }

    private static class DirectReceiveFixture {
        private final NetworkNode networkNode = mock(NetworkNode.class);
        private final EncryptionService encryptionService = mock(EncryptionService.class);
        private final Connection connection = mock(Connection.class);
        private final PrefixedSealedAndSignedMessage sealedMessage = mock(PrefixedSealedAndSignedMessage.class);
        private final SealedAndSigned sealedAndSigned = mock(SealedAndSigned.class);
        private final NetworkEnvelope payload;
        private final DecryptedMessageWithPubKey decryptedMessageWithPubKey;
        private final P2PService p2PService;

        private DirectReceiveFixture(NetworkEnvelope payload,
                                     NodeAddress envelopeSender,
                                     NodeAddress connectionSender) throws Exception {
            this(payload, TestUtils.generateKeyPair().getPublic(), envelopeSender, connectionSender);
        }

        private DirectReceiveFixture(NetworkEnvelope payload,
                                     PublicKey sealedPayloadSignaturePubKey,
                                     NodeAddress envelopeSender,
                                     NodeAddress connectionSender) throws Exception {
            this.payload = payload;
            decryptedMessageWithPubKey = new DecryptedMessageWithPubKey(payload, sealedPayloadSignaturePubKey);
            when(sealedMessage.getSealedAndSigned()).thenReturn(sealedAndSigned);
            when(sealedMessage.getSenderNodeAddress()).thenReturn(envelopeSender);
            when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(connectionSender));
            when(encryptionService.decryptAndVerify(sealedAndSigned)).thenReturn(decryptedMessageWithPubKey);
            p2PService = newP2PService(networkNode, encryptionService);
        }
    }

    private static class DirectSendFixture {
        private final NetworkNode networkNode = mock(NetworkNode.class);
        private final EncryptionService encryptionService = mock(EncryptionService.class);
        private final P2PService p2PService;

        private DirectSendFixture() {
            this(mock(KeyRing.class));
        }

        private DirectSendFixture(KeyPair signatureKeyPair) {
            this(keyRing(signatureKeyPair));
        }

        private DirectSendFixture(KeyRing keyRing) {
            when(networkNode.getNodeAddress()).thenReturn(MY_NODE_ADDRESS);
            p2PService = newP2PService(networkNode, encryptionService, keyRing);
        }
    }

    private static KeyRing keyRing(KeyPair signatureKeyPair) {
        KeyRing keyRing = mock(KeyRing.class);
        when(keyRing.getSignatureKeyPair()).thenReturn(signatureKeyPair);
        return keyRing;
    }
}
