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

import io.bitsquare.BitsquareException;
import io.bitsquare.network.MessageBroker;
import io.bitsquare.network.BootstrapState;
import io.bitsquare.network.ClientNode;
import io.bitsquare.network.ConnectionType;
import io.bitsquare.network.NetworkException;
import io.bitsquare.network.Node;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.io.IOException;

import java.security.KeyPair;
import java.security.PublicKey;

import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nullable;

import javax.inject.Inject;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The fully bootstrapped P2PNode which is responsible himself for his availability in the messaging system. It saves
 * for instance the IP address periodically.
 * This class is offering generic functionality of TomP2P needed for Bitsquare, like data and domain protection.
 * It does not handle any domain aspects of Bitsquare.
 */
public class TomP2PNode implements ClientNode {
    private static final Logger log = LoggerFactory.getLogger(TomP2PNode.class);

    private KeyPair keyPair;
    private PeerAddress storedPeerAddress;
    private PeerDHT peerDHT;
    private BootstrappedPeerBuilder bootstrappedPeerBuilder;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PNode(BootstrappedPeerBuilder bootstrappedPeerBuilder) {
        this.bootstrappedPeerBuilder = bootstrappedPeerBuilder;
    }

    // for unit testing
    TomP2PNode(KeyPair keyPair, PeerDHT peerDHT) {
        this.keyPair = keyPair;
        this.peerDHT = peerDHT;
        peerDHT.peerBean().keyPair(keyPair);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Observable<BootstrapState> bootstrap(KeyPair keyPair, MessageBroker messageBroker) {
        checkNotNull(keyPair, "keyPair must not be null.");

        this.keyPair = keyPair;
        bootstrappedPeerBuilder.setKeyPair(keyPair);

        Subject<BootstrapState, BootstrapState> bootstrapStateSubject = BehaviorSubject.create();

        bootstrappedPeerBuilder.getBootstrapState().addListener((ov, oldValue, newValue) -> {
            log.debug("BootstrapState changed " + newValue);
            bootstrapStateSubject.onNext(newValue);
        });

        SettableFuture<PeerDHT> bootstrapFuture = bootstrappedPeerBuilder.start();
        Futures.addCallback(bootstrapFuture, new FutureCallback<PeerDHT>() {
            @Override
            public void onSuccess(@Nullable PeerDHT peerDHT) {
                if (peerDHT != null) {
                    TomP2PNode.this.peerDHT = peerDHT;
                    setupTimerForIPCheck();
                    setupReplyHandler(messageBroker);
                    try {
                        storeAddress();
                    } catch (NetworkException e) {
                        bootstrapStateSubject.onError(e);
                    }
                    bootstrapStateSubject.onCompleted();
                }
                else {
                    log.error("Error at bootstrap: peerDHT = null");
                    bootstrapStateSubject.onError(new BitsquareException("Error at bootstrap: peerDHT = null"));
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                log.error("Exception at bootstrap " + t.getMessage());
                bootstrapStateSubject.onError(t);
            }
        });

        return bootstrapStateSubject.asObservable();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Generic DHT methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO remove all security features for the moment. There are some problems with a "wrong signature!" msg in
    // the logs
    public FuturePut putDomainProtectedData(Number160 locationKey, Data data) {
        log.trace("putDomainProtectedData");
        return peerDHT.put(locationKey).data(data).start();
    }

    public FuturePut putData(Number160 locationKey, Data data) {
        log.trace("putData");
        return peerDHT.put(locationKey).data(data).start();
    }

    public FutureGet getDomainProtectedData(Number160 locationKey, PublicKey publicKey) {
        log.trace("getDomainProtectedData");
        return peerDHT.get(locationKey).start();
    }

    public FutureGet getData(Number160 locationKey) {
        //log.trace("getData");
        return peerDHT.get(locationKey).start();
    }

    public FuturePut addProtectedData(Number160 locationKey, Data data) {
        log.trace("addProtectedData");
        return peerDHT.add(locationKey).data(data).start();
    }

    public FutureRemove removeFromDataMap(Number160 locationKey, Data data) {
        Number160 contentKey = data.hash();
        log.trace("removeFromDataMap with contentKey " + contentKey.toString());
        return peerDHT.remove(locationKey).contentKey(contentKey).start();
    }

    public FutureGet getDataMap(Number160 locationKey) {
        log.trace("getDataMap");
        return peerDHT.get(locationKey).all().start();
    }

    public FutureDirect sendData(PeerAddress peerAddress, Object payLoad) {
        log.trace("sendData");
        FutureDirect futureDirect = peerDHT.peer().sendDirect(peerAddress).object(payLoad).start();
        futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("sendMessage completed");
                }
                else {
                    log.error("sendData failed with Reason " + futureDirect.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                log.error("Exception at sendData " + t.toString());
            }
        });

        return futureDirect;
    }

//
//    public FuturePut putDomainProtectedData(Number160 locationKey, Data data) {
//        log.trace("putDomainProtectedData");
//        data.protectEntry(keyPair);
//        final Number160 ownerKeyHash = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
//        return peerDHT.put(locationKey).data(data).keyPair(keyPair).domainKey(ownerKeyHash).protectDomain().start();
//    }
//
//    // No protection, everybody can write.
//    public FuturePut putData(Number160 locationKey, Data data) {
//        log.trace("putData");
//        return peerDHT.put(locationKey).data(data).start();
//    }
//
//    // Not public readable. Only users with the public key of the peer who stored the data can read that data
//    public FutureGet getDomainProtectedData(Number160 locationKey, PublicKey publicKey) {
//        log.trace("getDomainProtectedData");
//        final Number160 ownerKeyHash = Utils.makeSHAHash(publicKey.getEncoded());
//        return peerDHT.get(locationKey).domainKey(ownerKeyHash).start();
//    }
//
//    // No protection, everybody can read.
//    public FutureGet getData(Number160 locationKey) {
//        log.trace("getData");
//        return peerDHT.get(locationKey).start();
//    }
//
//    // No domain protection, but entry protection
//    public FuturePut addProtectedData(Number160 locationKey, Data data) {
//        log.trace("addProtectedData");
//        data.protectEntry(keyPair);
//        log.trace("addProtectedData with contentKey " + data.hash().toString());
//        return peerDHT.add(locationKey).data(data).keyPair(keyPair).start();
//    }
//
//    // No domain protection, but entry protection
//    public FutureRemove removeFromDataMap(Number160 locationKey, Data data) {
//        log.trace("removeFromDataMap");
//        Number160 contentKey = data.hash();
//        log.trace("removeFromDataMap with contentKey " + contentKey.toString());
//        return peerDHT.remove(locationKey).contentKey(contentKey).keyPair(keyPair).start();
//    }
//
//    // Public readable
//    public FutureGet getDataMap(Number160 locationKey) {
//        log.trace("getDataMap");
//        return peerDHT.get(locationKey).all().start();
//    }

    // Send signed payLoad to peer
//    public FutureDirect sendData(PeerAddress peerAddress, Object payLoad) {
//        // use 30 seconds as max idle time before connection get closed
//        FuturePeerConnection futurePeerConnection = peerDHT.peer().createPeerConnection(peerAddress, 30000);
//        FutureDirect futureDirect = peerDHT.peer().sendDirect(futurePeerConnection).object(payLoad).sign().start();
//        futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
//            @Override
//            public void operationComplete(BaseFuture future) throws Exception {
//                if (futureDirect.isSuccess()) {
//                    log.debug("sendMessage completed");
//                }
//                else {
//                    log.error("sendData failed with Reason " + futureDirect.failedReason());
//                }
//            }
//
//            @Override
//            public void exceptionCaught(Throwable t) throws Exception {
//                log.error("Exception at sendData " + t.toString());
//            }
//        });
//
//        return futureDirect;
//    }
//


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupReplyHandler(MessageBroker messageBroker) {
        peerDHT.peer().objectDataReply((sender, request) -> {
            log.debug("handleMessage peerAddress " + sender);
            log.debug("handleMessage message " + request);

            if (!sender.equals(peerDHT.peer().peerAddress())) {
                if (messageBroker != null)
                    messageBroker.handleMessage(request, new TomP2PPeer(sender));
            }
            else {
                throw new RuntimeException("Received msg from myself. That must never happen.");
            }
            return null;
        });
    }

    private void setupTimerForIPCheck() {
        Timer timer = new Timer();
        long checkIfIPChangedPeriod = 600 * 1000;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (storedPeerAddress != null && peerDHT != null
                        && !storedPeerAddress.equals(peerDHT.peerAddress()))
                    try {
                        storeAddress();
                    } catch (NetworkException e) {
                        e.printStackTrace();
                    }
            }
        }, checkIfIPChangedPeriod, checkIfIPChangedPeriod);
    }

    private void storeAddress() throws NetworkException {
        try {
            FuturePut futurePut = saveAddress();
            futurePut.addListener(new BaseFutureListener<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    if (future.isSuccess()) {
                        storedPeerAddress = peerDHT.peerAddress();
                        log.debug("storedPeerAddress = " + storedPeerAddress);
                    }
                    else {
                        log.error("storedPeerAddress not successful");
                        throw new NetworkException("Storing address was not successful. Reason: "
                                + future.failedReason());
                    }
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception {
                    log.error("Exception at storedPeerAddress " + t.toString());
                    throw new NetworkException("Exception at storeAddress.", t);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Exception at storePeerAddress " + e.toString());
            throw new NetworkException("Exception at storeAddress.", e);
        }
    }

    private FuturePut saveAddress() throws IOException {
        Number160 locationKey = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
        Data data = new Data(new TomP2PPeer(peerDHT.peerAddress()));
        log.debug("storePeerAddress " + peerDHT.peerAddress().toString());
        return putDomainProtectedData(locationKey, data);
    }

    @Override
    public ConnectionType getConnectionType() {
        BootstrapState bootstrapState = bootstrappedPeerBuilder.getBootstrapState().get();
        switch (bootstrapState) {
            case DISCOVERY_DIRECT_SUCCEEDED:
                return ConnectionType.DIRECT;
            case DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED:
                return ConnectionType.MANUAL_PORT_FORWARDING;
            case DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED:
                return ConnectionType.AUTO_PORT_FORWARDING;
            case RELAY_SUCCEEDED:
                return ConnectionType.RELAY;
            default:
                throw new BitsquareException("Invalid bootstrap state: %s", bootstrapState);
        }
    }

    @Override
    public Node getAddress() {
        PeerAddress peerAddress = peerDHT.peerBean().serverPeerAddress();
        return Node.at(
                peerDHT.peerID().toString(),
                peerAddress.inetAddress().getHostAddress(),
                peerAddress.peerSocketAddress().tcpPort());
    }

    @Override
    public Node getBootstrapNodeAddress() {
        return bootstrappedPeerBuilder.getBootstrapNode();
    }
}
