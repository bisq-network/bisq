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

package io.bitsquare.trade.tomp2p;

import io.bitsquare.network.Message;
import io.bitsquare.network.Peer;
import io.bitsquare.network.tomp2p.TomP2PNode;
import io.bitsquare.network.tomp2p.TomP2PPeer;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.handlers.MessageHandler;
import io.bitsquare.trade.listeners.GetPeerAddressListener;
import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.user.User;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.peers.Number160;
import net.tomp2p.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * That service delivers direct messaging and DHT functionality from the TomP2P library
 * It is the translating domain specific functionality to the messaging layer.
 * The TomP2P library codebase shall not be used outside that service.
 * That way we limit the dependency of the TomP2P library only to that class (and it's sub components).
 * <p/>
 */
public class TomP2PTradeMessageService implements TradeMessageService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PTradeMessageService.class);

    private final TomP2PNode tomP2PNode;
    private final List<MessageHandler> messageHandlers = new ArrayList<>();
    private Executor executor;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TomP2PTradeMessageService(User user, TomP2PNode tomP2PNode) {
        this.tomP2PNode = tomP2PNode;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Find peer address by publicKey
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void getPeerAddress(PublicKey publicKey, GetPeerAddressListener listener) {
        final Number160 locationKey = Utils.makeSHAHash(publicKey.getEncoded());
        FutureGet futureGet = tomP2PNode.getDomainProtectedData(locationKey, publicKey);

        futureGet.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception {
                if (baseFuture.isSuccess() && futureGet.data() != null) {
                    final Peer peer = (Peer) futureGet.data().object();
                    executor.execute(() -> listener.onResult(peer));
                }
                else {
                    log.error("getPeerAddress failed. failedReason = " + baseFuture.failedReason());
                    executor.execute(listener::onFailed);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade messages
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    public void addMessageHandler(MessageHandler listener) {
        if (!messageHandlers.add(listener))
            throw new RuntimeException("Add listener did not change list. Probably listener has been already added.");
    }

    public void removeMessageHandler(MessageHandler listener) {
        if (!messageHandlers.remove(listener))
            throw new RuntimeException("Try to remove listener which was never added.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleMessage(Object message, Peer sender) {
        if (message instanceof Message && sender instanceof TomP2PPeer) {
            executor.execute(() -> messageHandlers.stream().forEach(e -> e.handleMessage((Message) message, sender)));
        }
    }
}
