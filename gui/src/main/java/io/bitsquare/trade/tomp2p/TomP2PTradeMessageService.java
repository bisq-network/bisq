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
import io.bitsquare.trade.listeners.GetPeerAddressListener;
import io.bitsquare.trade.listeners.IncomingMessageListener;
import io.bitsquare.trade.listeners.OutgoingMessageListener;
import io.bitsquare.user.User;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import javafx.application.Platform;

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
 * TODO: improve callbacks that Platform.runLater is not necessary. We call usually that methods form teh UI thread.
 */
public class TomP2PTradeMessageService implements TradeMessageService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PTradeMessageService.class);

    private final TomP2PNode tomP2PNode;
    private final User user;

    private final List<IncomingMessageListener> incomingMessageListeners = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PTradeMessageService(User user, TomP2PNode tomP2PNode) {
        this.user = user;
        this.tomP2PNode = tomP2PNode;
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
                    Platform.runLater(() -> listener.onResult(peer));
                }
                else {
                    log.error("getPeerAddress failed. failedReason = " + baseFuture.failedReason());
                    Platform.runLater(listener::onFailed);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendMessage(Peer peer, Message message,
                            OutgoingMessageListener listener) {
        if (!(peer instanceof TomP2PPeer)) {
            throw new IllegalArgumentException("peer must be of type TomP2PPeer");
        }
        FutureDirect futureDirect = tomP2PNode.sendData(((TomP2PPeer) peer).getPeerAddress(), message);
        futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    Platform.runLater(listener::onResult);
                }
                else {
                    log.error("sendMessage failed with reason " + futureDirect.failedReason());
                    Platform.runLater(listener::onFailed);
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                Platform.runLater(listener::onFailed);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addIncomingMessageListener(IncomingMessageListener listener) {
        incomingMessageListeners.add(listener);
    }

    public void removeIncomingMessageListener(IncomingMessageListener listener) {
        incomingMessageListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleMessage(Object message, Peer sender) {
        if (message instanceof Message) {
            Platform.runLater(() -> incomingMessageListeners.stream().forEach(e ->
                    e.onMessage((Message) message, sender)));
        }
    }
}
