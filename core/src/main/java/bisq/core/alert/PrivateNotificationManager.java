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

import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.network.NetworkEnvelope;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.Charsets;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.security.SignatureException;

import java.math.BigInteger;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static org.bitcoinj.core.Utils.HEX;

public class PrivateNotificationManager {
    private static final Logger log = LoggerFactory.getLogger(PrivateNotificationManager.class);

    private final P2PService p2PService;
    private final MailboxMessageService mailboxMessageService;
    private final KeyRing keyRing;
    private final ObjectProperty<PrivateNotificationPayload> privateNotificationMessageProperty = new SimpleObjectProperty<>();

    // Pub key for developer global privateNotification message
    private final String pubKeyAsHex;

    private ECKey privateNotificationSigningKey;
    @Nullable
    private PrivateNotificationMessage privateNotificationMessage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PrivateNotificationManager(P2PService p2PService,
                                      MailboxMessageService mailboxMessageService,
                                      KeyRing keyRing,
                                      @Named(Config.IGNORE_DEV_MSG) boolean ignoreDevMsg,
                                      @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        this.p2PService = p2PService;
        this.mailboxMessageService = mailboxMessageService;
        this.keyRing = keyRing;

        if (!ignoreDevMsg) {
            this.p2PService.addDecryptedDirectMessageListener(this::handleMessage);
            this.mailboxMessageService.addDecryptedMailboxListener(this::handleMessage);
        }
        pubKeyAsHex = useDevPrivilegeKeys ?
                DevEnv.DEV_PRIVILEGE_PUB_KEY :
                "02ba7c5de295adfe57b60029f3637a2c6b1d0e969a8aaefb9e0ddc3a7963f26925";
    }

    private void handleMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, NodeAddress senderNodeAddress) {
        NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
        if (networkEnvelope instanceof PrivateNotificationMessage) {
            privateNotificationMessage = (PrivateNotificationMessage) networkEnvelope;
            log.info("Received PrivateNotificationMessage from {} with uid={}",
                    senderNodeAddress, privateNotificationMessage.getUid());
            if (privateNotificationMessage.getSenderNodeAddress().equals(senderNodeAddress)) {
                final PrivateNotificationPayload privateNotification = privateNotificationMessage.getPrivateNotificationPayload();
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

    public ReadOnlyObjectProperty<PrivateNotificationPayload> privateNotificationProperty() {
        return privateNotificationMessageProperty;
    }

    public boolean sendPrivateNotificationMessageIfKeyIsValid(PrivateNotificationPayload privateNotification,
                                                              PubKeyRing pubKeyRing,
                                                              NodeAddress peersNodeAddress,
                                                              String privKeyString,
                                                              SendMailboxMessageListener sendMailboxMessageListener) {
        boolean isKeyValid = isKeyValid(privKeyString);
        if (isKeyValid) {
            signAndAddSignatureToPrivateNotificationMessage(privateNotification);

            PrivateNotificationMessage message = new PrivateNotificationMessage(privateNotification,
                    p2PService.getNetworkNode().getNodeAddress(),
                    UUID.randomUUID().toString());
            log.info("Send {} to peer {}. uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getUid());
            mailboxMessageService.sendEncryptedMailboxMessage(peersNodeAddress,
                    pubKeyRing,
                    message,
                    sendMailboxMessageListener);
        }

        return isKeyValid;
    }

    public void removePrivateNotification() {
        if (privateNotificationMessage != null) {
            mailboxMessageService.removeMailboxMsg(privateNotificationMessage);
        }
    }

    private boolean isKeyValid(String privKeyString) {
        try {
            privateNotificationSigningKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
            return pubKeyAsHex.equals(Utils.HEX.encode(privateNotificationSigningKey.getPubKey()));
        } catch (Throwable t) {
            return false;
        }
    }

    private void signAndAddSignatureToPrivateNotificationMessage(PrivateNotificationPayload privateNotification) {
        String privateNotificationMessageAsHex = Utils.HEX.encode(privateNotification.getMessage().getBytes(Charsets.UTF_8));
        String signatureAsBase64 = privateNotificationSigningKey.signMessage(privateNotificationMessageAsHex);
        privateNotification.setSigAndPubKey(signatureAsBase64, keyRing.getSignatureKeyPair().getPublic());
    }

    private boolean verifySignature(PrivateNotificationPayload privateNotification) {
        String privateNotificationMessageAsHex = Utils.HEX.encode(privateNotification.getMessage().getBytes(Charsets.UTF_8));
        try {
            ECKey.fromPublicOnly(HEX.decode(pubKeyAsHex)).verifyMessage(privateNotificationMessageAsHex, privateNotification.getSignatureAsBase64());
            return true;
        } catch (SignatureException e) {
            log.warn("verifySignature failed");
            return false;
        }
    }


}
