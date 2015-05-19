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

package io.bitsquare.p2p.tomp2p;

import io.bitsquare.crypto.CryptoException;
import io.bitsquare.crypto.CryptoService;
import io.bitsquare.crypto.MessageWithPubKey;
import io.bitsquare.crypto.PubKeyRing;
import io.bitsquare.crypto.SealedAndSignedMessage;
import io.bitsquare.p2p.DecryptedMessageHandler;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.util.Utilities;

import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureDirect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomP2PMessageService extends TomP2PService implements MessageService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PMessageService.class);
    private static final int MAX_MESSAGE_SIZE = 100 * 1024; // 34 kb is currently the max size used

    private final CopyOnWriteArrayList<MessageHandler> messageHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DecryptedMessageHandler> decryptedMessageHandlers = new CopyOnWriteArrayList<>();
    private final MailboxService mailboxService;
    private final CryptoService<MailboxMessage> cryptoService;

    @Inject
    public TomP2PMessageService(TomP2PNode tomP2PNode, MailboxService mailboxService, CryptoService<MailboxMessage> cryptoService) {
        super(tomP2PNode);
        this.mailboxService = mailboxService;
        this.cryptoService = cryptoService;
    }


    @Override
    public void bootstrapCompleted() {
        super.bootstrapCompleted();
        setupReplyHandler();
    }

    @Override
    public void shutDown() {
        super.shutDown();
    }

    @Override
    public void sendEncryptedMessage(Peer peer, PubKeyRing pubKeyRing, Message message, boolean useMailboxIfUnreachable, SendMessageListener listener) {
        assert pubKeyRing != null;

        log.debug("sendMessage called");
        if (peer == null)
            throw new IllegalArgumentException("Peer must not be null");
        else if (!(peer instanceof TomP2PPeer))
            throw new IllegalArgumentException("Peer must be of type TomP2PPeer");

        try {
            final Message encryptedMessage = cryptoService.encryptAndSignMessage(pubKeyRing, message);

            openRequestsUp();
            FutureDirect futureDirect = peerDHT.peer().sendDirect(((TomP2PPeer) peer).getPeerAddress()).object(encryptedMessage).start();
            futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
                                         @Override
                                         public void operationComplete(BaseFuture future) throws Exception {
                                             if (future.isSuccess()) {
                                                 openRequestsDown();
                                                 log.debug("sendMessage completed");
                                                 executor.execute(listener::handleResult);
                                             }
                                             else {
                                                 log.info("sendMessage failed. We will try to send the message to the mailbox. Fault reason:  " +
                                                         futureDirect.failedReason());
                                                 if (useMailboxIfUnreachable) {
                                                     sendMailboxMessage(pubKeyRing, (SealedAndSignedMessage) encryptedMessage, listener);
                                                 }
                                                 else {
                                                     openRequestsDown();
                                                     log.error("Send message was not successful");
                                                     executor.execute(listener::handleFault);
                                                 }
                                             }
                                         }

                                         @Override
                                         public void exceptionCaught(Throwable t) throws Exception {
                                             log.info("sendMessage failed with exception. We will try to send the message to the mailbox. Exception: "
                                                     + t.getMessage());
                                             if (useMailboxIfUnreachable) {
                                                 sendMailboxMessage(pubKeyRing, (SealedAndSignedMessage) encryptedMessage, listener);
                                             }
                                             else {
                                                 openRequestsDown();
                                                 log.error("Send message was not successful");
                                                 executor.execute(listener::handleFault);
                                             }
                                         }
                                     }
            );
        } catch (Throwable t) {
            openRequestsDown();
            t.printStackTrace();
            log.error(t.getMessage());
            executor.execute(listener::handleFault);
        }
    }

    private void sendMailboxMessage(PubKeyRing pubKeyRing, SealedAndSignedMessage message, SendMessageListener listener) {
        log.info("sendMailboxMessage called");
        mailboxService.addMessage(
                pubKeyRing,
                message,
                () -> {
                    openRequestsDown();
                    log.debug("Message successfully added to peers mailbox.");
                    executor.execute(listener::handleResult);
                },
                (errorMessage, throwable) -> {
                    openRequestsDown();
                    log.error("Message failed to add to peers mailbox.");
                    executor.execute(listener::handleFault);
                }
        );
    }

    @Override
    public void addMessageHandler(MessageHandler listener) {
        if (!messageHandlers.add(listener))
            log.error("Add listener did not change list. Probably listener has been already added.");
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        if (!messageHandlers.remove(listener))
            log.error("Try to remove listener which was never added.");
    }

    @Override
    public void addDecryptedMessageHandler(DecryptedMessageHandler listener) {
        if (!decryptedMessageHandlers.add(listener))
            log.error("Add listener did not change list. Probably listener has been already added.");
    }

    @Override
    public void removeDecryptedMessageHandler(DecryptedMessageHandler listener) {
        if (!decryptedMessageHandlers.remove(listener))
            log.error("Try to remove listener which was never added.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupReplyHandler() {
        peerDHT.peer().objectDataReply((sender, message) -> {
            //log.debug("Incoming message with peerAddress " + sender);
            //log.debug("Incoming message with type " + message);

            int messageSize = 0;
            if (message != null)
                messageSize = Utilities.objectToBytArray(message).length;

            log.debug("Incoming message with size " + messageSize);

            if (!sender.equals(peerDHT.peer().peerAddress())) {
                if (messageSize == 0)
                    log.warn("Received msg is null");
                else if (messageSize > MAX_MESSAGE_SIZE)
                    log.warn("Received msg size of  {} is exceeding the max message size of {}.",
                            Utilities.objectToBytArray(message).length, MAX_MESSAGE_SIZE);
                else if (message instanceof SealedAndSignedMessage)
                    executor.execute(() -> decryptedMessageHandlers.stream().forEach(e -> {
                                MessageWithPubKey messageWithPubKey = null;
                                try {
                                    messageWithPubKey = getDecryptedMessageWithPubKey((SealedAndSignedMessage) message);
                                    //log.debug("decrypted message " + messageWithPubKey.getMessage());
                                    e.handleMessage(messageWithPubKey, new TomP2PPeer(sender));
                                } catch (CryptoException e1) {
                                    e1.printStackTrace();
                                    log.warn("decryptAndVerifyMessage msg failed", e1.getMessage());
                                }
                            }
                    ));
                else if (message instanceof Message)
                    executor.execute(() -> messageHandlers.stream().forEach(e -> e.handleMessage((Message) message, new TomP2PPeer(sender))));
                else
                    log.warn("We got an object which is not type of Message. Object = " + message);
            }
            else {
                log.error("Received msg from myself. That must never happen.");
            }
            return true;
        });
    }

    private MessageWithPubKey getDecryptedMessageWithPubKey(SealedAndSignedMessage message) throws CryptoException {
        return cryptoService.decryptAndVerifyMessage(message);
    }
}
