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

import io.bitsquare.network.BootstrapState;
import io.bitsquare.network.Node;
import io.bitsquare.persistence.Persistence;

import com.google.common.util.concurrent.SettableFuture;

import com.google.inject.name.Named;

import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.security.KeyPair;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.ChannelClientConfiguration;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.nat.FutureNAT;
import net.tomp2p.nat.FutureRelayNAT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMap;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerMapConfiguration;
import net.tomp2p.peers.PeerStatistic;
import net.tomp2p.replication.IndirectReplication;
import net.tomp2p.utils.Utils;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Creates a DHT peer and bootstraps to the network via a bootstrap node
 */
public class BootstrappedPeerFactory {
    private static final Logger log = LoggerFactory.getLogger(BootstrappedPeerFactory.class);

    public static final String BOOTSTRAP_NODE_KEY = "bootstrapNode";
    static final String NETWORK_INTERFACE_KEY = "interface";
    static final String NETWORK_INTERFACE_UNSPECIFIED = "<unspecified>";

    private KeyPair keyPair;
    private final int port;
    private final Node bootstrapNode;
    private final String networkInterface;
    private final Persistence persistence;

    private final SettableFuture<PeerDHT> settableFuture = SettableFuture.create();
    public final ObjectProperty<BootstrapState> bootstrapState = new SimpleObjectProperty<>();
    private Peer peer;
    private PeerDHT peerDHT;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BootstrappedPeerFactory(Persistence persistence,
                                   @Named(Node.PORT_KEY) int port,
                                   @Named(BOOTSTRAP_NODE_KEY) Node bootstrapNode,
                                   @Named(NETWORK_INTERFACE_KEY) String networkInterface) {
        this.persistence = persistence;
        this.port = port;
        this.bootstrapNode = bootstrapNode;
        this.networkInterface = networkInterface;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setKeyPair(@NotNull KeyPair keyPair) {
        this.keyPair = keyPair;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SettableFuture<PeerDHT> start() {
        try {
            setState(BootstrapState.PEER_CREATION, "We create a P2P node.");

            Number160 peerId = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
            PeerMapConfiguration pmc = new PeerMapConfiguration(peerId).peerNoVerification();
            PeerMap pm = new PeerMap(pmc);
            ChannelClientConfiguration cc = PeerBuilder.createDefaultChannelClientConfiguration();
            cc.maxPermitsTCP(100);
            cc.maxPermitsUDP(100);
            Bindings bindings = new Bindings();
            if (!NETWORK_INTERFACE_UNSPECIFIED.equals(networkInterface))
                bindings.addInterface(networkInterface);

            peer = new PeerBuilder(keyPair).ports(port).peerMap(pm).bindings(bindings)
                    .channelClientConfiguration(cc).start();
            peerDHT = new PeerBuilderDHT(peer).start();
            new IndirectReplication(peerDHT).start();

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified) {
                    log.debug("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    log.debug("Peer removed: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    // log.debug("Peer updated: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }
            });

            // We save last successful bootstrap method.
            // Reset it to BootstrapState.DIRECT_SUCCESS after 5 start ups.
            Object bootstrapCounterObject = persistence.read(this, "bootstrapCounter");
            int bootstrapCounter = 0;
            if (bootstrapCounterObject instanceof Integer)
                bootstrapCounter = (int) bootstrapCounterObject + 1;

            if (bootstrapCounter > 5) {
                persistence.write(this, "lastSuccessfulBootstrap", BootstrapState.DIRECT_SUCCESS);
                bootstrapCounter = 0;
            }
            persistence.write(this, "bootstrapCounter", bootstrapCounter);

            BootstrapState lastSuccessfulBootstrap = BootstrapState.DIRECT_SUCCESS;
            Object lastSuccessfulBootstrapObject = persistence.read(this, "lastSuccessfulBootstrap");
            if (lastSuccessfulBootstrapObject instanceof BootstrapState)
                lastSuccessfulBootstrap = (BootstrapState) lastSuccessfulBootstrapObject;
            else
                persistence.write(this, "lastSuccessfulBootstrap", lastSuccessfulBootstrap);

            log.debug("lastSuccessfulBootstrap = " + lastSuccessfulBootstrap);

            // just temporary always start with trying direct connection
            lastSuccessfulBootstrap = BootstrapState.DIRECT_SUCCESS;

            switch (lastSuccessfulBootstrap) {
                case RELAY_SUCCESS:
                    bootstrapWithRelay();
                    break;
                case NAT_SUCCESS:
                    tryPortForwarding();
                    break;
                case DIRECT_SUCCESS:
                default:
                    discover();
                    break;
            }
        } catch (IOException e) {
            handleError(BootstrapState.PEER_CREATION, "Cannot create peer with port: " + port + ". Exception: " + e);
        }

        return settableFuture;
    }

    void shutDown() {
        if (peerDHT != null)
            peerDHT.shutdown();
    }

    // 1. Attempt: Try to discover our outside visible address
    private void discover() {
        setState(BootstrapState.DIRECT_INIT, "We are starting discovery against a bootstrap node.");
        FutureDiscover futureDiscover = peer.discover().peerAddress(getBootstrapAddress()).start();
        futureDiscover.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    setState(BootstrapState.DIRECT_SUCCESS, "We are directly connected and visible to other peers.");
                    bootstrap(BootstrapState.DIRECT_SUCCESS);
                }
                else {
                    setState(BootstrapState.DIRECT_NOT_SUCCEEDED, "We are probably behind a NAT and not reachable to " +
                            "other peers. We try to setup automatic port forwarding.");
                    tryPortForwarding();
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(BootstrapState.DIRECT_FAILED, "Exception at discover: " + t.getMessage());
            }
        });
    }

    // 2. Attempt: Try to set up port forwarding with UPNP and NAT-PMP
    private void tryPortForwarding() {
        setState(BootstrapState.NAT_INIT, "We are trying with automatic port forwarding.");
        FutureDiscover futureDiscover = peer.discover().peerAddress(getBootstrapAddress()).start();
        PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
        FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
        futureNAT.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    setState(BootstrapState.NAT_SETUP_DONE, "Automatic port forwarding is setup. " +
                            "We need to do a discover process again.");
                    // we need a second discover process
                    discoverAfterPortForwarding();
                }
                else {
                    setState(BootstrapState.NAT_NOT_SUCCEEDED, "Port forwarding has failed. " +
                            "We try to use a relay as next step.");
                    bootstrapWithRelay();
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(BootstrapState.NAT_FAILED, "Exception at port forwarding: " + t.getMessage());
            }
        });
    }

    // Try to determine our outside visible address after port forwarding is setup
    private void discoverAfterPortForwarding() {
        FutureDiscover futureDiscover = peer.discover().peerAddress(getBootstrapAddress()).start();
        futureDiscover.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    setState(BootstrapState.NAT_SUCCESS, "Discover with automatic port forwarding was successful.");
                    bootstrap(BootstrapState.NAT_SUCCESS);
                }
                else {
                    handleError(BootstrapState.NAT_FAILED, "Discover with automatic port forwarding has failed " +
                            futureDiscover.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(BootstrapState.NAT_FAILED, "Exception at discover: " + t.getMessage());
            }
        });
    }

    // 3. Attempt: We try to use another peer as relay
    private void bootstrapWithRelay() {
        setState(BootstrapState.RELAY_INIT, "We try to use another peer as relay.");
        FutureDiscover futureDiscover = peer.discover().peerAddress(getBootstrapAddress()).start();
        PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
        FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
        FutureRelayNAT futureRelayNAT = peerNAT.startRelay(futureDiscover, futureNAT);
        futureRelayNAT.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    setState(BootstrapState.RELAY_SUCCESS, "Bootstrap using relay was successful.");
                    bootstrap(BootstrapState.RELAY_SUCCESS);
                }
                else {
                    handleError(BootstrapState.RELAY_FAILED, "Bootstrap using relay has failed " +
                            futureRelayNAT.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(BootstrapState.RELAY_FAILED, "Exception at bootstrapWithRelay: " + t.getMessage());
            }
        });
    }

    private void bootstrap(BootstrapState state) {
        FutureBootstrap futureBootstrap = peer.bootstrap().peerAddress(getBootstrapAddress()).start();
        futureBootstrap.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (futureBootstrap.isSuccess()) {
                    setState(state, "Bootstrap successful.");
                    persistence.write(BootstrappedPeerFactory.this, "lastSuccessfulBootstrap", state);
                    settableFuture.set(peerDHT);
                }
                else {
                    handleError(BootstrapState.DIRECT_NOT_SUCCEEDED, "Bootstrapping failed. " +
                            futureBootstrap.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(BootstrapState.DIRECT_FAILED, "Exception at bootstrap: " + t.getMessage());
            }
        });
    }

    private PeerAddress getBootstrapAddress() {
        try {
            return new PeerAddress(Number160.createHash(bootstrapNode.getName()),
                    InetAddress.getByName(bootstrapNode.getIp()),
                    bootstrapNode.getPort(),
                    bootstrapNode.getPort());
        } catch (UnknownHostException e) {
            log.error("getBootstrapAddress failed: " + e.getMessage());
            return null;
        }
    }

    private void setState(BootstrapState bootstrapState, String message) {
        setState(bootstrapState, message, true);
    }

    private void setState(BootstrapState bootstrapState, String message, boolean isSuccess) {
        if (isSuccess)
            log.info(message);
        else
            log.error(message);

        bootstrapState.setMessage(message);
        Platform.runLater(() -> this.bootstrapState.set(bootstrapState));
    }

    private void handleError(BootstrapState state, String errorMessage) {
        setState(state, errorMessage, false);
        persistence.write(this, "lastSuccessfulBootstrap", BootstrapState.DIRECT_SUCCESS);
        peerDHT.shutdown();
        settableFuture.setException(new Exception(errorMessage));
    }
}
