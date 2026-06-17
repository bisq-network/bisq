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

package bisq.core.support;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.PublicKey;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportManagerAckTest {
    private static final String TRADE_ID = "trade-id";
    private static final String MISSING_UID = "missing-source-uid";
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer.onion", 9999);

    @Test
    void ackWithUnknownSourceMessageDoesNotPersistOrResolvePeerKey() {
        P2PService p2PService = mock(P2PService.class);
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
        when(p2PService.isBootstrapped()).thenReturn(true);
        when(walletsSetup.isDownloadComplete()).thenReturn(true);
        when(walletsSetup.hasSufficientPeersForBroadcast()).thenReturn(true);

        TestSupportManager manager = new TestSupportManager(p2PService, walletsSetup);
        ArgumentCaptor<DecryptedDirectMessageListener> listenerCaptor =
                ArgumentCaptor.forClass(DecryptedDirectMessageListener.class);
        verify(p2PService).addDecryptedDirectMessageListener(listenerCaptor.capture());

        manager.onAllServicesInitialized();
        AckMessage ackMessage = new AckMessage(PEER_NODE_ADDRESS,
                AckMessageSourceType.MEDIATION_MESSAGE,
                ChatMessage.class.getSimpleName(),
                MISSING_UID,
                TRADE_ID,
                true,
                null);

        listenerCaptor.getValue().onDirectMessage(
                new DecryptedMessageWithPubKey(ackMessage, Sig.generateKeyPair().getPublic()),
                PEER_NODE_ADDRESS);

        assertEquals(0, manager.getPeerPubKeyRingCalls());
        assertEquals(0, manager.getRequestPersistenceCalls());
    }

    private static final class TestSupportManager extends SupportManager {
        private final AtomicInteger getPeerPubKeyRingCalls = new AtomicInteger();
        private final AtomicInteger requestPersistenceCalls = new AtomicInteger();

        private TestSupportManager(P2PService p2PService, WalletsSetup walletsSetup) {
            super(p2PService, walletsSetup);
        }

        private int getPeerPubKeyRingCalls() {
            return getPeerPubKeyRingCalls.get();
        }

        private int getRequestPersistenceCalls() {
            return requestPersistenceCalls.get();
        }

        @Override
        protected void onSupportMessage(SupportMessage networkEnvelope, PublicKey senderSignaturePubKey) {
        }

        @Override
        public NodeAddress getPeerNodeAddress(ChatMessage message) {
            return PEER_NODE_ADDRESS;
        }

        @Override
        public PubKeyRing getPeerPubKeyRing(ChatMessage message) {
            getPeerPubKeyRingCalls.incrementAndGet();
            return null;
        }

        @Override
        public SupportType getSupportType() {
            return SupportType.MEDIATION;
        }

        @Override
        public boolean channelOpen(ChatMessage message) {
            return true;
        }

        @Override
        public List<ChatMessage> getAllChatMessages(String tradeId) {
            return List.of();
        }

        @Override
        public void addAndPersistChatMessage(ChatMessage message) {
        }

        @Override
        public void requestPersistence() {
            requestPersistenceCalls.incrementAndGet();
        }

        @Override
        protected AckMessageSourceType getAckMessageSourceType() {
            return AckMessageSourceType.MEDIATION_MESSAGE;
        }
    }
}
