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
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.BaseP2PService;
import io.bitsquare.p2p.ClientNode;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.security.KeyPair;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import javax.inject.Inject;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import net.tomp2p.dht.PeerDHT;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatistic;

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
    private final Subject<BootstrappedPeerBuilder.State, BootstrappedPeerBuilder.State> bootstrapStateSubject;
    private final List<ResultHandler> resultHandlers = new CopyOnWriteArrayList<>();
    private final IntegerProperty numPeers = new SimpleIntegerProperty(0);


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

    public void setExecutor(Executor executor) {
        bootstrappedPeerBuilder.setExecutor(executor);
    }

    public Observable<BootstrappedPeerBuilder.State> bootstrap(KeyPair keyPair) {
        bootstrappedPeerBuilder.setKeyPair(keyPair);

        bootstrappedPeerBuilder.getState().addListener((ov, oldValue, newValue) -> {
            log.debug("BootstrapState changed " + newValue);
            bootstrapStateSubject.onNext(newValue);
        });

        SettableFuture<PeerDHT> bootstrapFuture = bootstrappedPeerBuilder.start();
        Futures.addCallback(bootstrapFuture, new FutureCallback<PeerDHT>() {
            @Override
            public void onSuccess(@Nullable PeerDHT peerDHT) {
                if (peerDHT != null) {
                    TomP2PNode.this.peerDHT = peerDHT;

                    BaseP2PService.getUserThread().execute(() -> numPeers.set(peerDHT.peerBean().peerMap().all().size()));
                    log.debug("Number of peers = " + peerDHT.peerBean().peerMap().all().size());

                    peerDHT.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                        @Override
                        public void peerInserted(PeerAddress peerAddress, boolean b) {
                            BaseP2PService.getUserThread().execute(() -> numPeers.set(peerDHT.peerBean().peerMap().all().size()));
                            log.debug("peerInserted " + peerAddress);
                            log.debug("Number of peers = " + peerDHT.peerBean().peerMap().all().size());
                        }

                        @Override
                        public void peerRemoved(PeerAddress peerAddress, PeerStatistic peerStatistic) {
                            BaseP2PService.getUserThread().execute(() -> numPeers.set(peerDHT.peerBean().peerMap().all().size()));
                            log.debug("peerRemoved " + peerAddress);
                            log.debug("Number of peers = " + peerDHT.peerBean().peerMap().all().size());
                        }

                        @Override
                        public void peerUpdated(PeerAddress peerAddress, PeerStatistic peerStatistic) {
                            BaseP2PService.getUserThread().execute(() -> numPeers.set(peerDHT.peerBean().peerMap().all().size()));
                            // log.debug("peerUpdated " + peerAddress);
                            // log.debug("Number of peers = " + peerDHT.peerBean().peerMap().all().size());
                        }
                    });
                 /*   peerDHT.peerBean().addPeerStatusListener(new PeerStatusListener() {
                        @Override
                        public boolean peerFailed(PeerAddress peerAddress, PeerException e) {
                            return false;
                        }

                        @Override
                        public boolean peerFound(PeerAddress peerAddress, PeerAddress peerAddress1, PeerConnection peerConnection, RTT rtt) {
                            BaseP2PService.getUserThread().execute(() -> numPeers.set(peerDHT.peerBean().peerMap().size()));
                            return false;
                        }
                    });*/

                    resultHandlers.stream().forEach(ResultHandler::handleResult);
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

    public PeerDHT getPeerDHT() {
        return peerDHT;
    }

    @Override
    public BootstrappedPeerBuilder.ConnectionType getConnectionType() {
        return bootstrappedPeerBuilder.getConnectionType();
    }

    public String getClientNodeInfo() {
        PeerAddress peerAddress = peerDHT.peerBean().serverPeerAddress();
        return "IP='" + peerAddress.inetAddress().getHostAddress() + '\'' +
                "; P2P network ID='" + peerDHT.peer().p2pId() + '\'' +
                "; port=" + peerAddress.peerSocketAddress().tcpPort();
    }

    public void addResultHandler(ResultHandler resultHandler) {
        resultHandlers.add(resultHandler);
    }

    public void removeResultHandler(ResultHandler resultHandler) {
        resultHandlers.remove(resultHandler);
    }

    public int getNumPeers() {
        return numPeers.get();
    }

    public ReadOnlyIntegerProperty numPeersProperty() {
        return numPeers;
    }
}
