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

package io.bitsquare.msg.tomp2p;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.msg.Message;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.ArbitratorListener;
import io.bitsquare.msg.listeners.BootstrapListener;
import io.bitsquare.msg.listeners.GetPeerAddressListener;
import io.bitsquare.msg.listeners.IncomingMessageListener;
import io.bitsquare.msg.listeners.OutgoingMessageListener;
import io.bitsquare.network.Peer;
import io.bitsquare.network.tomp2p.TomP2PPeer;
import io.bitsquare.user.User;

import java.io.IOException;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import javafx.application.Platform;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.tomp2p.BaseFutureUtil.isSuccess;


/**
 * That facade delivers direct messaging and DHT functionality from the TomP2P library
 * It is the translating domain specific functionality to the messaging layer.
 * The TomP2P library codebase shall not be used outside that facade.
 * That way we limit the dependency of the TomP2P library only to that class (and it's sub components).
 * <p>
 * TODO: improve callbacks that Platform.runLater is not necessary. We call usually that methods form teh UI thread.
 */
class TomP2PMessageFacade implements MessageFacade {
    private static final Logger log = LoggerFactory.getLogger(TomP2PMessageFacade.class);
    private static final String ARBITRATORS_ROOT = "ArbitratorsRoot";

    private final TomP2PNode p2pNode;
    private final User user;

    private final List<ArbitratorListener> arbitratorListeners = new ArrayList<>();
    private final List<IncomingMessageListener> incomingMessageListeners = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PMessageFacade(User user, TomP2PNode p2pNode) {
        this.user = user;
        this.p2pNode = p2pNode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init(int port, BootstrapListener bootstrapListener) {
        p2pNode.setMessageBroker(this);
        p2pNode.setKeyPair(user.getMessageKeyPair());

        p2pNode.start(port, bootstrapListener);
    }

    public void shutDown() {
        if (p2pNode != null)
            p2pNode.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Find peer address by publicKey
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void getPeerAddress(PublicKey publicKey, GetPeerAddressListener listener) {
        final Number160 locationKey = Utils.makeSHAHash(publicKey.getEncoded());
        FutureGet futureGet = p2pNode.getDomainProtectedData(locationKey, publicKey);

        futureGet.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception {
                if (isSuccess(baseFuture) && futureGet.data() != null) {
                    final Peer peer = (Peer) futureGet.data().object();
                    Platform.runLater(() -> listener.onResult(peer));
                }
                else {
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
        FutureDirect futureDirect = p2pNode.sendData(((TomP2PPeer) peer).getPeerAddress(), message);
        futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (isSuccess(futureDirect)) {
                    Platform.runLater(listener::onResult);
                }
                else {
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
    // Arbitrators
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addArbitrator(Arbitrator arbitrator) {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        try {
            final Data arbitratorData = new Data(arbitrator);

            FuturePut addFuture = p2pNode.addProtectedData(locationKey, arbitratorData);
            addFuture.addListener(new BaseFutureAdapter<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    Platform.runLater(() -> arbitratorListeners.stream().forEach(listener ->
                    {
                        try {
                            Object arbitratorDataObject = arbitratorData.object();
                            if (arbitratorDataObject instanceof Arbitrator) {
                                listener.onArbitratorAdded((Arbitrator) arbitratorDataObject);
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            e.printStackTrace();
                            log.error(e.toString());
                        }
                    }));

                    if (isSuccess(addFuture)) {
                        log.trace("Add arbitrator to DHT was successful. Stored data: [key: " + locationKey + ", " +
                                "values: " + arbitratorData + "]");
                    }
                    else {
                        log.error("Add arbitrator to DHT failed with reason:" + addFuture.failedReason());
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeArbitrator(Arbitrator arbitrator) throws IOException {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        final Data arbitratorData = new Data(arbitrator);
        FutureRemove removeFuture = p2pNode.removeFromDataMap(locationKey, arbitratorData);
        removeFuture.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                Platform.runLater(() -> arbitratorListeners.stream().forEach(listener ->
                {
                    for (Data arbitratorData : removeFuture.dataMap().values()) {
                        try {
                            Object arbitratorDataObject = arbitratorData.object();
                            if (arbitratorDataObject instanceof Arbitrator) {
                                Arbitrator arbitrator = (Arbitrator) arbitratorDataObject;
                                listener.onArbitratorRemoved(arbitrator);
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                }));
                if (isSuccess(removeFuture)) {
                    log.trace("Remove arbitrator from DHT was successful. Stored data: [key: " + locationKey + ", " +
                            "values: " + arbitratorData + "]");
                }
                else {
                    log.error("Remove arbitrators from DHT failed with reason:" + removeFuture.failedReason());
                }
            }
        });
    }

    public void getArbitrators(Locale languageLocale) {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        FutureGet futureGet = p2pNode.getDataMap(locationKey);
        futureGet.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception {
                Platform.runLater(() -> arbitratorListeners.stream().forEach(listener ->
                {
                    List<Arbitrator> arbitrators = new ArrayList<>();
                    for (Data arbitratorData : futureGet.dataMap().values()) {
                        try {
                            Object arbitratorDataObject = arbitratorData.object();
                            if (arbitratorDataObject instanceof Arbitrator) {
                                arbitrators.add((Arbitrator) arbitratorDataObject);
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            e.printStackTrace();
                            log.error("Get arbitrators from DHT failed with exception:" + e.getMessage());
                        }
                    }

                    listener.onArbitratorsReceived(arbitrators);
                }));
                if (isSuccess(baseFuture)) {
                    log.trace("Get arbitrators from DHT was successful. Stored data: [key: " + locationKey + ", " +
                            "values: " + futureGet.dataMap() + "]");
                }
                else {
                    log.error("Get arbitrators from DHT failed with reason:" + baseFuture.failedReason());
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addArbitratorListener(ArbitratorListener listener) {
        arbitratorListeners.add(listener);
    }

    public void removeArbitratorListener(ArbitratorListener listener) {
        arbitratorListeners.remove(listener);
    }

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
