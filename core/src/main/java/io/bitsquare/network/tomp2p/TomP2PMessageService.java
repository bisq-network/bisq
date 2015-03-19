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

package io.bitsquare.network.tomp2p;

import io.bitsquare.network.Message;
import io.bitsquare.network.MessageHandler;
import io.bitsquare.network.MessageService;
import io.bitsquare.network.Peer;
import io.bitsquare.network.listener.SendMessageListener;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureDirect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * That service delivers direct messaging and DHT functionality from the TomP2P library
 * It is the translating domain specific functionality to the messaging layer.
 * The TomP2P library codebase shall not be used outside that service.
 * That way we limit the dependency of the TomP2P library only to that class (and it's sub components).
 * <p/>
 */
public class TomP2PMessageService implements MessageService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PMessageService.class);

    private final TomP2PNode tomP2PNode;
    private final CopyOnWriteArrayList<MessageHandler> messageHandlers = new CopyOnWriteArrayList<>();
    private Executor executor;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TomP2PMessageService(TomP2PNode tomP2PNode) {
        this.tomP2PNode = tomP2PNode;
    }

    @Override
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Messages
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendMessage(Peer peer, Message message, SendMessageListener listener) {
        if (!(peer instanceof TomP2PPeer)) {
            throw new IllegalArgumentException("peer must be of type TomP2PPeer");
        }
        FutureDirect futureDirect = tomP2PNode.sendData(((TomP2PPeer) peer).getPeerAddress(), message);
        futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    executor.execute(listener::handleResult);
                }
                else {
                    log.error("sendMessage failed with reason " + futureDirect.failedReason());
                    executor.execute(listener::handleFault);
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                executor.execute(listener::handleFault);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addMessageHandler(MessageHandler listener) {
        if (!messageHandlers.add(listener))
            throw new RuntimeException("Add listener did not change list. Probably listener has been already added.");
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        if (!messageHandlers.remove(listener))
            throw new RuntimeException("Try to remove listener which was never added.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleMessage(Message message, Peer sender) {
        if (sender instanceof TomP2PPeer) {
            executor.execute(() -> messageHandlers.stream().forEach(e -> e.handleMessage((Message) message, sender)));
        }
    }
}
