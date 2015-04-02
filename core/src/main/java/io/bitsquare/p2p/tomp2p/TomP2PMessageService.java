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

import io.bitsquare.crypto.Bucket;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.p2p.EncryptedMailboxMessage;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.SendMessageListener;

import java.security.PublicKey;

import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureDirect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomP2PMessageService extends TomP2PService implements MessageService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PMessageService.class);

    private final CopyOnWriteArrayList<MessageHandler> messageHandlers = new CopyOnWriteArrayList<>();
    private final MailboxService mailboxService;
    private final EncryptionService<MailboxMessage> encryptionService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PMessageService(TomP2PNode tomP2PNode, MailboxService mailboxService, EncryptionService<MailboxMessage> encryptionService) {
        super(tomP2PNode);
        this.mailboxService = mailboxService;
        this.encryptionService = encryptionService;
    }


    @Override
    public void bootstrapCompleted() {
        super.bootstrapCompleted();
        setupReplyHandler();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageService implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendMessage(Peer peer, Message message, SendMessageListener listener) {
        sendMessage(peer, message, null, null, listener);
    }

    @Override
    public void sendMessage(Peer peer, Message message, PublicKey recipientP2pSigPubKey, PublicKey recipientP2pEncryptPubKey,
                            SendMessageListener listener) {

        if (peer == null)
            throw new IllegalArgumentException("Peer must not be null");
        else if (!(peer instanceof TomP2PPeer))
            throw new IllegalArgumentException("Peer must be of type TomP2PPeer");

        FutureDirect futureDirect = peerDHT.peer().sendDirect(((TomP2PPeer) peer).getPeerAddress()).object(message).start();
        futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("sendMessage completed");
                    executor.execute(listener::handleResult);
                }
                else {
                    if (recipientP2pSigPubKey != null && recipientP2pEncryptPubKey != null) {
                        log.info("sendMessage failed. We will try to send the message to the mailbox. Fault reason:  " + futureDirect.failedReason());
                        sendMailboxMessage(recipientP2pSigPubKey, recipientP2pEncryptPubKey, (MailboxMessage) message, listener);
                    }
                    else {
                        log.error("sendMessage failed with reason " + futureDirect.failedReason());
                        executor.execute(listener::handleFault);
                    }
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                if (recipientP2pSigPubKey != null && recipientP2pEncryptPubKey != null) {
                    log.info("sendMessage failed with exception. We will try to send the message to the mailbox. Exception: " + t.getMessage());
                    sendMailboxMessage(recipientP2pSigPubKey, recipientP2pEncryptPubKey, (MailboxMessage) message, listener);
                }
                else {
                    log.error("sendMessage failed with exception " + t.getMessage());
                    executor.execute(listener::handleFault);
                }
            }
        });
    }

    private void sendMailboxMessage(PublicKey recipientP2pSigPubKey, PublicKey recipientP2pEncryptPubKey, MailboxMessage message, SendMessageListener 
            listener) {
        Bucket bucket = null;
        log.info("sendMailboxMessage called");
        try {
            bucket = encryptionService.encryptObject(recipientP2pEncryptPubKey, message);
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            executor.execute(listener::handleFault);
        }
        EncryptedMailboxMessage encrypted = new EncryptedMailboxMessage(bucket);
        mailboxService.addMessage(recipientP2pSigPubKey,
                encrypted,
                () -> {
                    log.debug("Message successfully added to peers mailbox.");
                    executor.execute(listener::handleResult);
                },
                (errorMessage, throwable) -> {
                    log.error("Message failed to  add to peers mailbox.");
                    executor.execute(listener::handleFault);
                }
        );
    }

    @Override
    public void addMessageHandler(MessageHandler listener) {
        if (!messageHandlers.add(listener))
            throw new IllegalArgumentException("Add listener did not change list. Probably listener has been already added.");
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        if (!messageHandlers.remove(listener))
            throw new IllegalArgumentException("Try to remove listener which was never added.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupReplyHandler() {
        peerDHT.peer().objectDataReply((sender, message) -> {
            log.debug("handleMessage peerAddress " + sender);
            log.debug("handleMessage message " + message);

            if (!sender.equals(peerDHT.peer().peerAddress())) {
                if (message instanceof Message)
                    executor.execute(() -> messageHandlers.stream().forEach(e -> e.handleMessage((Message) message, new TomP2PPeer(sender))));
                else
                    throw new RuntimeException("We got an object which is not type of Message. That must never happen. Request object = " + message);
            }
            else {
                throw new RuntimeException("Received msg from myself. That must never happen.");
            }

            return true;
        });
    }

}
