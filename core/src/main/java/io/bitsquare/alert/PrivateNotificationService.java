/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.alert;

import io.bitsquare.crypto.DecryptedMsgWithPubKey;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.messaging.DecryptedDirectMessageListener;
import io.bitsquare.p2p.messaging.DecryptedMailboxListener;
import io.bitsquare.p2p.messaging.SendMailboxMessageListener;
import io.bitsquare.trade.offer.Offer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Used to load global privateNotification messages.
 * The message is signed by the project developers private key and use data protection.
 */
public class PrivateNotificationService {
    private static final Logger log = LoggerFactory.getLogger(PrivateNotificationService.class);
    private final P2PService p2PService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PrivateNotificationService(P2PService p2PService) {
        this.p2PService = p2PService;
    }

    public void addDecryptedMailboxListener(DecryptedMailboxListener listener) {
        p2PService.addDecryptedMailboxListener(listener);
    }

    public void addDecryptedDirectMessageListener(DecryptedDirectMessageListener listener) {
        p2PService.addDecryptedDirectMessageListener(listener);
    }


    public void sendPrivateNotificationMessage(PrivateNotification privateNotification, Offer offer, SendMailboxMessageListener sendMailboxMessageListener) {
        p2PService.sendEncryptedMailboxMessage(offer.getOffererNodeAddress(),
                offer.getPubKeyRing(),
                new PrivateNotificationMessage(privateNotification, p2PService.getNetworkNode().getNodeAddress()),
                sendMailboxMessageListener);
    }

    public void removePrivateNotification(DecryptedMsgWithPubKey decryptedMsgWithPubKey) {
        p2PService.removeEntryFromMailbox(decryptedMsgWithPubKey);
    }
}
