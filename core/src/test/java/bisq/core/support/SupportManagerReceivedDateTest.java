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
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.messaging.DecryptedMailboxListener;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.security.KeyPair;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportManagerReceivedDateTest {
    private static final String TRADE_ID = "tradeId";
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer.onion", 9999);
    // A value the peer could put into the message, it must not be kept
    private static final long RECEIVED_DATE_FROM_PEER = 42;

    private final KeyPair peerSignatureKeyPair = Sig.generateKeyPair();
    private final PubKeyRing peerPubKeyRing = pubKeyRing(peerSignatureKeyPair);
    private final ObservableList<ChatMessage> chatMessages = FXCollections.observableArrayList();
    private P2PService p2PService;
    private MailboxMessageService mailboxMessageService;

    @BeforeEach
    void setUp() {
        p2PService = mock(P2PService.class);
        mailboxMessageService = mock(MailboxMessageService.class);
        NetworkNode networkNode = mock(NetworkNode.class);
        when(networkNode.getNodeAddress()).thenReturn(new NodeAddress("me.onion", 9999));
        when(p2PService.getNetworkNode()).thenReturn(networkNode);
        when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
        when(p2PService.isBootstrapped()).thenReturn(true);
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.isDownloadComplete()).thenReturn(true);
        when(walletsSetup.hasSufficientPeersForBroadcast()).thenReturn(true);

        // The trade chat was opened before, so it has its rules message
        ChatMessage rules = new ChatMessage(SupportType.TRADE,
                TRADE_ID,
                0,
                false,
                "rules",
                new NodeAddress("null:0000"),
                0);
        rules.setSystemMessage(true);
        chatMessages.add(rules);

        PubKeyRing myPubKeyRing = pubKeyRing(Sig.generateKeyPair());
        TraderChatManager manager = new TraderChatManager(p2PService,
                walletsSetup,
                tradeManager(myPubKeyRing),
                mock(ClosedTradableManager.class),
                mock(FailedTradesManager.class),
                myPubKeyRing);
        manager.onAllServicesInitialized();
    }

    @Test
    void aDirectChatMessageGetsTheLocalReceiveTime() {
        long before = System.currentTimeMillis();
        receiveDirectly(chatMessageFromPeer());

        assertReceivedSince(before);
    }

    @Test
    void aChatMessageFromTheMailboxGetsTheLocalReceiveTime() {
        long before = System.currentTimeMillis();
        receiveFromTheMailbox(chatMessageFromPeer());

        assertReceivedSince(before);
    }

    // The direct send is reported as failed after the message arrived, so the peer stores it in the mailbox as well
    @Test
    void aChatMessageReceivedTwiceKeepsTheFirstReceiveTime() {
        int baseCurrencyNetwork = Version.getBaseCurrencyNetwork();
        // The message version, which the application sets at startup, is needed for the proto
        Version.setBaseCryptoNetworkId(BaseCurrencyNetwork.BTC_REGTEST.ordinal());
        try {
            ChatMessage chatMessage = chatMessageFromPeer();
            ChatMessage copyFromTheMailbox =
                    ChatMessage.fromPayloadProto(chatMessage.toProtoNetworkEnvelope().getChatMessage());
            receiveDirectly(chatMessage);
            // A fixed first receive time, so that a later stamp would be seen even within the same millisecond
            long firstReceivedDate = 1_700_000_000_000L;
            chatMessage.setReceivedDate(firstReceivedDate);

            receiveFromTheMailbox(copyFromTheMailbox);

            List<ChatMessage> received = receivedChatMessages();
            assertEquals(1, received.size());
            assertSame(chatMessage, received.get(0));
            assertEquals(firstReceivedDate, chatMessage.getReceivedDate());
        } finally {
            Version.setBaseCryptoNetworkId(baseCurrencyNetwork);
        }
    }

    // Only the node itself adds system messages to a trade chat
    @Test
    void aDirectSystemMessageOfThePeerIsStoredAsAMessageOfThePeer() {
        ChatMessage chatMessage = chatMessageFromPeer();
        chatMessage.setSystemMessage(true);

        receiveDirectly(chatMessage);

        assertFalse(chatMessage.isSystemMessage());
        assertEquals(List.of(chatMessage), receivedChatMessages());
    }

    @Test
    void aSystemMessageOfThePeerFromTheMailboxIsStoredAsAMessageOfThePeer() {
        ChatMessage chatMessage = chatMessageFromPeer();
        chatMessage.setSystemMessage(true);

        receiveFromTheMailbox(chatMessage);

        assertFalse(chatMessage.isSystemMessage());
        assertEquals(List.of(chatMessage), receivedChatMessages());
    }

    // Only this node knows whether it showed a message
    @Test
    void theDisplayedFlagOfAReceivedMessageIsReset() {
        ChatMessage chatMessage = chatMessageFromPeer();
        chatMessage.setWasDisplayed(true);

        receiveDirectly(chatMessage);

        assertFalse(chatMessage.isWasDisplayed());
    }

    private void receiveDirectly(ChatMessage chatMessage) {
        ArgumentCaptor<DecryptedDirectMessageListener> listener =
                ArgumentCaptor.forClass(DecryptedDirectMessageListener.class);
        verify(p2PService).addDecryptedDirectMessageListener(listener.capture());
        listener.getValue().onDirectMessage(new DecryptedMessageWithPubKey(chatMessage,
                peerSignatureKeyPair.getPublic()), PEER_NODE_ADDRESS);
    }

    private void receiveFromTheMailbox(ChatMessage chatMessage) {
        ArgumentCaptor<DecryptedMailboxListener> listener = ArgumentCaptor.forClass(DecryptedMailboxListener.class);
        verify(mailboxMessageService).addDecryptedMailboxListener(listener.capture());
        listener.getValue().onMailboxMessageAdded(new DecryptedMessageWithPubKey(chatMessage,
                peerSignatureKeyPair.getPublic()), PEER_NODE_ADDRESS);
    }

    private void assertReceivedSince(long before) {
        List<ChatMessage> received = receivedChatMessages();
        assertEquals(1, received.size());
        long receivedDate = received.get(0).getReceivedDate();
        assertTrue(receivedDate >= before && receivedDate <= System.currentTimeMillis(),
                "receivedDate=" + receivedDate);
    }

    private List<ChatMessage> receivedChatMessages() {
        return chatMessages.stream()
                .filter(chatMessage -> !chatMessage.isSystemMessage())
                .collect(Collectors.toList());
    }

    private TradeManager tradeManager(PubKeyRing myPubKeyRing) {
        Contract contract = mock(Contract.class);
        when(contract.getPeersPubKeyRing(myPubKeyRing)).thenReturn(peerPubKeyRing);
        when(contract.getPeersNodeAddress(myPubKeyRing)).thenReturn(PEER_NODE_ADDRESS);

        Trade trade = mock(Trade.class);
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.getDate()).thenReturn(new Date(0));
        when(trade.getContract()).thenReturn(contract);
        when(trade.getChatMessages()).thenReturn(chatMessages);
        doAnswer(invocation -> chatMessages.add(invocation.getArgument(0)))
                .when(trade).addAndPersistChatMessage(any());

        TradeManager tradeManager = mock(TradeManager.class);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(trade));
        return tradeManager;
    }

    private static ChatMessage chatMessageFromPeer() {
        ChatMessage chatMessage = new ChatMessage(SupportType.TRADE,
                TRADE_ID,
                0,
                false,
                "message",
                PEER_NODE_ADDRESS);
        chatMessage.setReceivedDate(RECEIVED_DATE_FROM_PEER);
        return chatMessage;
    }

    private static PubKeyRing pubKeyRing(KeyPair signatureKeyPair) {
        return new PubKeyRing(signatureKeyPair.getPublic(), Encryption.generateKeyPair().getPublic());
    }
}
