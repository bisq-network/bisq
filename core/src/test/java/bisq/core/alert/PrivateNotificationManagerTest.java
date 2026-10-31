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

package bisq.core.alert;

import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.messaging.DecryptedMailboxListener;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import java.security.KeyPair;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Base64;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Anyone can send a private notification. Only one signed with the notification key is shown, and a malformed
 * signature does not make the handling of the message throw.
 */
class PrivateNotificationManagerTest {
    private static final NodeAddress SENDER = new NodeAddress("sender.onion", 9999);
    private static final String TEXT = "text";

    private final P2PService p2PService = mock(P2PService.class);
    private final MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
    private final PrivateNotificationManager manager = new PrivateNotificationManager(p2PService,
            mock(NetworkNode.class), mailboxMessageService, mock(KeyRing.class), false, true);
    private final KeyPair senderKeyPair = Sig.generateKeyPair();

    @Test
    void aNotificationSignedWithTheNotificationKeyIsShown() {
        PrivateNotificationPayload notification = notification(signedWith(notificationKey()));

        receive(message(notification, "uid"));

        assertSame(notification, manager.privateNotificationProperty().get());
    }

    // A compact signature with r = 0, for which the point decompression of bitcoinj throws an
    // IllegalArgumentException
    @Test
    void aNotificationWithAMalformedSignatureIsIgnored() {
        receive(message(notification(malformedSignature()), "uid"));

        assertNull(manager.privateNotificationProperty().get());
    }

    // The mailbox messages of a start are handled in one batch, which an exception would end
    @Test
    void aNotificationWithAMalformedSignatureFromTheMailboxIsIgnored() {
        receiveFromMailbox(message(notification(malformedSignature()), "uid"));

        assertNull(manager.privateNotificationProperty().get());
    }

    private static ECKey notificationKey() {
        return ECKey.fromPrivate(new BigInteger(1, Utils.HEX.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));
    }

    private static String signedWith(ECKey key) {
        return key.signMessage(Utils.HEX.encode(TEXT.getBytes(StandardCharsets.UTF_8)));
    }

    private static String malformedSignature() {
        byte[] signature = new byte[65];
        signature[0] = 31;
        signature[64] = 1;
        return Base64.getEncoder().encodeToString(signature);
    }

    private PrivateNotificationPayload notification(String signatureAsBase64) {
        PrivateNotificationPayload notification = new PrivateNotificationPayload(TEXT);
        notification.setSigAndPubKey(signatureAsBase64, senderKeyPair.getPublic());
        return notification;
    }

    private static PrivateNotificationMessage message(PrivateNotificationPayload notification, String uid) {
        return new PrivateNotificationMessage(notification, SENDER, uid);
    }

    private void receive(PrivateNotificationMessage message) {
        ArgumentCaptor<DecryptedDirectMessageListener> listener =
                ArgumentCaptor.forClass(DecryptedDirectMessageListener.class);
        verify(p2PService).addDecryptedDirectMessageListener(listener.capture());
        listener.getValue().onDirectMessage(new DecryptedMessageWithPubKey(message, senderKeyPair.getPublic()), SENDER);
    }

    private void receiveFromMailbox(PrivateNotificationMessage message) {
        ArgumentCaptor<DecryptedMailboxListener> listener = ArgumentCaptor.forClass(DecryptedMailboxListener.class);
        verify(mailboxMessageService).addDecryptedMailboxListener(listener.capture());
        listener.getValue().onMailboxMessageAdded(new DecryptedMessageWithPubKey(message, senderKeyPair.getPublic()),
                SENDER);
    }
}
