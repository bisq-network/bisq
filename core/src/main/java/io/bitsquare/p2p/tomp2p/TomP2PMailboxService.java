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

import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.offer.OfferBookService;
import io.bitsquare.p2p.EncryptedMailboxMessage;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.user.User;

import java.io.IOException;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomP2PMailboxService extends TomP2PDHTService implements MailboxService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PMailboxService.class);
    private static final int TTL = 15 * 24 * 60 * 60;    // the message is default 15 days valid, as a max trade period might be about 2 weeks.

    private final List<OfferBookService.Listener> offerRepositoryListeners = new ArrayList<>();

    @Inject
    public TomP2PMailboxService(TomP2PNode tomP2PNode, User user) {
        super(tomP2PNode, user);
    }

    @Override
    public void bootstrapCompleted() {
        super.bootstrapCompleted();
    }

    @Override
    public void shutDown() {
        super.shutDown();
    }

    @Override
    public void addMessage(PublicKey publicKey, EncryptedMailboxMessage message, ResultHandler resultHandler, FaultHandler faultHandler) {
        try {
            final Data data = new Data(message);
            data.ttlSeconds(TTL);
            log.trace("Add message to DHT requested. Added data: [locationKey: " + getLocationKey(publicKey) +
                    ", hash: " + data.hash().toString() + "]");

            FuturePut futurePut = addDataToMapOfProtectedDomain(getLocationKey(publicKey), data, publicKey);
            futurePut.addListener(new BaseFutureListener<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    if (future.isSuccess()) {
                        executor.execute(() -> {
                            resultHandler.handleResult();

                            log.trace("Add message to mailbox was successful. Added data: [locationKey: " + getLocationKey(publicKey) +
                                    ", value: " + data + "]");
                        });
                    }
                }

                @Override
                public void exceptionCaught(Throwable ex) throws Exception {
                    executor.execute(() -> faultHandler.handleFault("Add message to mailbox failed.", ex));
                }
            });
        } catch (IOException ex) {
            executor.execute(() -> faultHandler.handleFault("Add message to mailbox failed.", ex));
        }
    }

    @Override
    public void removeMessage(PublicKey publicKey, EncryptedMailboxMessage message, ResultHandler resultHandler, FaultHandler faultHandler) {
        try {
            final Data data = new Data(message);
            log.trace("Remove message from DHT requested. Removed data: [locationKey: " + getLocationKey(publicKey) +
                    ", hash: " + data.hash().toString() + "]");
            FutureRemove futureRemove = removeDataFromMapOfMyProtectedDomain(getLocationKey(publicKey), data);
            futureRemove.addListener(new BaseFutureListener<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    // We don't test futureRemove.isSuccess() as this API does not fit well to that operation, 
                    // it might change in future to something like foundAndRemoved and notFound
                    // See discussion at: https://github.com/tomp2p/TomP2P/issues/57#issuecomment-62069840
                    log.trace("isRemoved? " + futureRemove.isRemoved());
                    executor.execute(() -> {
                        resultHandler.handleResult();
                    });
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception {
                    log.error("Remove message from DHT failed. Error: " + t.getMessage());
                    faultHandler.handleFault("Remove message from DHT failed. Error: " + t.getMessage(), t);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Remove message from DHT failed. Error: " + e.getMessage());
            faultHandler.handleFault("Remove message from DHT failed. Error: " + e.getMessage(), e);
        }
    }

    @Override
    public void getMessages(PublicKey publicKey, MailboxMessagesResultHandler resultHandler) {
        log.trace("Get messages from DHT requested for locationKey: " + getLocationKey(publicKey));
        FutureGet futureGet = getDataFromMapOfMyProtectedDomain(getLocationKey(publicKey));
        futureGet.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    final Map<Number640, Data> dataMap = futureGet.dataMap();
                    List<EncryptedMailboxMessage> messages = new ArrayList<>();
                    if (dataMap != null) {
                        for (Data messageData : dataMap.values()) {
                            try {
                                Object messageDataObject = messageData.object();
                                if (messageDataObject instanceof EncryptedMailboxMessage) {
                                    messages.add((EncryptedMailboxMessage) messageDataObject);
                                }
                            } catch (ClassNotFoundException | IOException e) {
                                e.printStackTrace();
                            }
                        }
                        executor.execute(() -> resultHandler.handleResult(messages));
                    }

                    log.trace("Get messages from DHT was successful. Stored data: [key: " + getLocationKey(publicKey)
                            + ", values: " + futureGet.dataMap() + "]");
                }
                else {
                    final Map<Number640, Data> dataMap = futureGet.dataMap();
                    if (dataMap == null || dataMap.size() == 0) {
                        log.trace("Get messages from DHT delivered empty dataMap.");
                        executor.execute(() -> resultHandler.handleResult(new ArrayList<>()));
                    }
                    else {
                        log.error("Get messages from DHT  was not successful with reason:" + future.failedReason());
                    }
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Number160 getLocationKey(PublicKey publicKey) {
        return Number160.createHash("mailbox" + publicKey.hashCode());
    }

    public interface MailboxMessagesResultHandler {
        void handleResult(List<EncryptedMailboxMessage> messages);
    }
}