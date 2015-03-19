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

import io.bitsquare.BitsquareException;
import io.bitsquare.p2p.BootstrapState;
import io.bitsquare.p2p.ClientNode;
import io.bitsquare.p2p.ConnectionType;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.Node;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.security.KeyPair;

import javax.annotation.Nullable;

import javax.inject.Inject;

import net.tomp2p.dht.PeerDHT;
import net.tomp2p.peers.PeerAddress;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

public class TomP2PNode implements ClientNode {
    private static final Logger log = LoggerFactory.getLogger(TomP2PNode.class);

    private PeerDHT peerDHT;
    private BootstrappedPeerBuilder bootstrappedPeerBuilder;
    private final Subject<BootstrapState, BootstrapState> bootstrapStateSubject;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PNode(BootstrappedPeerBuilder bootstrappedPeerBuilder) {
        this.bootstrappedPeerBuilder = bootstrappedPeerBuilder;
        bootstrapStateSubject = BehaviorSubject.create();
    }

    // for unit testing
    TomP2PNode(KeyPair keyPair, PeerDHT peerDHT) {
        this.peerDHT = peerDHT;
        peerDHT.peerBean().keyPair(keyPair);
        bootstrapStateSubject = BehaviorSubject.create();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Observable<BootstrapState> bootstrap(KeyPair keyPair, MessageHandler messageHandler) {
        bootstrappedPeerBuilder.setKeyPair(keyPair);


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
                    setupReplyHandler(messageHandler);
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

    public Observable<BootstrapState> getBootstrapStateAsObservable() {
        return bootstrapStateSubject.asObservable();
    }

    public PeerDHT getPeerDHT() {
        return peerDHT;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupReplyHandler(MessageHandler messageHandler) {
        peerDHT.peer().objectDataReply((sender, request) -> {
            log.debug("handleMessage peerAddress " + sender);
            log.debug("handleMessage message " + request);

            if (!sender.equals(peerDHT.peer().peerAddress())) {
                if (request instanceof Message)
                    messageHandler.handleMessage((Message) request, new TomP2PPeer(sender));
                else
                    throw new RuntimeException("We got an object which is not type of Message. That must never happen. Request object = " + request);
            }
            else {
                throw new RuntimeException("Received msg from myself. That must never happen.");
            }

            return true;
        });
    }


}
