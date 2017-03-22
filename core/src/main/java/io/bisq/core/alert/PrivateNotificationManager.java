/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.alert;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bisq.common.app.DevEnv;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.network.p2p.SendMailboxMessageListener;
import io.bisq.network.p2p.storage.P2PService;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.alert.PrivateNotificationMessage;
import io.bisq.protobuffer.payload.alert.PrivateNotification;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SignatureException;

import static org.bitcoinj.core.Utils.HEX;

public class PrivateNotificationManager {
    private static final Logger log = LoggerFactory.getLogger(PrivateNotificationManager.class);

    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final ObjectProperty<PrivateNotification> privateNotificationMessageProperty = new SimpleObjectProperty<>();

    // Pub key for developer global privateNotification message
    private static final String pubKeyAsHex = DevEnv.USE_DEV_PRIVILEGE_KEYS ?
            DevEnv.DEV_PRIVILEGE_PUB_KEY :
            "02ba7c5de295adfe57b60029f3637a2c6b1d0e969a8aaefb9e0ddc3a7963f26925";

    private ECKey privateNotificationSigningKey;
    private DecryptedMsgWithPubKey decryptedMsgWithPubKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PrivateNotificationManager(P2PService p2PService, KeyRing keyRing, @Named(AppOptionKeys.IGNORE_DEV_MSG_KEY) boolean ignoreDevMsg) {
        this.p2PService = p2PService;
        this.keyRing = keyRing;

        if (!ignoreDevMsg) {
            this.p2PService.addDecryptedDirectMessageListener(this::handleMessage);
            this.p2PService.addDecryptedMailboxListener(this::handleMessage);
        }
    }

    private void handleMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress) {
        this.decryptedMsgWithPubKey = decryptedMsgWithPubKey;
        Message message = decryptedMsgWithPubKey.message;
        if (message instanceof PrivateNotificationMessage) {
            PrivateNotificationMessage privateNotificationMessage = (PrivateNotificationMessage) message;
            log.trace("Received privateNotificationMessage: " + privateNotificationMessage);
            if (privateNotificationMessage.getSenderNodeAddress().equals(senderNodeAddress)) {
                final PrivateNotification privateNotification = privateNotificationMessage.privateNotification;
                if (verifySignature(privateNotification))
                    privateNotificationMessageProperty.set(privateNotification);
            } else {
                log.warn("Peer address not matching for privateNotificationMessage");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyObjectProperty<PrivateNotification> privateNotificationProperty() {
        return privateNotificationMessageProperty;
    }

    public boolean sendPrivateNotificationMessageIfKeyIsValid(PrivateNotification privateNotification, PubKeyRing pubKeyRing, NodeAddress nodeAddress,
                                                              String privKeyString, SendMailboxMessageListener sendMailboxMessageListener) {
        boolean isKeyValid = isKeyValid(privKeyString);
        if (isKeyValid) {
            signAndAddSignatureToPrivateNotificationMessage(privateNotification);
            p2PService.sendEncryptedMailboxMessage(nodeAddress,
                    pubKeyRing,
                    new PrivateNotificationMessage(privateNotification, p2PService.getNetworkNode().getNodeAddress()),
                    sendMailboxMessageListener);
        }

        return isKeyValid;
    }

    public void removePrivateNotification() {
        p2PService.removeEntryFromMailbox(decryptedMsgWithPubKey);
    }

    private boolean isKeyValid(String privKeyString) {
        try {
            privateNotificationSigningKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
            return pubKeyAsHex.equals(Utils.HEX.encode(privateNotificationSigningKey.getPubKey()));
        } catch (Throwable t) {
            return false;
        }
    }

    private void signAndAddSignatureToPrivateNotificationMessage(PrivateNotification privateNotification) {
        String privateNotificationMessageAsHex = Utils.HEX.encode(privateNotification.message.getBytes());
        String signatureAsBase64 = privateNotificationSigningKey.signMessage(privateNotificationMessageAsHex);
        privateNotification.setSigAndPubKey(signatureAsBase64, keyRing.getSignatureKeyPair().getPublic());
    }

    private boolean verifySignature(PrivateNotification privateNotification) {
        String privateNotificationMessageAsHex = Utils.HEX.encode(privateNotification.message.getBytes());
        try {
            ECKey.fromPublicOnly(HEX.decode(pubKeyAsHex)).verifyMessage(privateNotificationMessageAsHex, privateNotification.getSignatureAsBase64());
            return true;
        } catch (SignatureException e) {
            log.warn("verifySignature failed");
            return false;
        }
    }


}
