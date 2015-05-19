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

import io.bitsquare.p2p.BootstrapNodes;
import io.bitsquare.p2p.Node;
import io.bitsquare.user.Preferences;

import com.google.common.util.concurrent.SettableFuture;

import com.google.inject.name.Named;

import java.io.IOException;

import java.security.KeyPair;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.ChannelClientConfiguration;
import net.tomp2p.connection.ChannelServerConfiguration;
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
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatistic;
import net.tomp2p.relay.tcp.TCPRelayClientConfig;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.DefaultEventExecutorGroup;


/**
 * Creates a DHT peer and bootstraps to the network via a bootstrap node
 */
public class BootstrappedPeerBuilder {
    private static final Logger log = LoggerFactory.getLogger(BootstrappedPeerBuilder.class);

    static final String NETWORK_INTERFACE_KEY = "interface";
    static final String NETWORK_INTERFACE_UNSPECIFIED = "<unspecified>";
    static final String USE_MANUAL_PORT_FORWARDING_KEY = "node.useManualPortForwarding";

    public enum ConnectionType {
        UNDEFINED, DIRECT, MANUAL_PORT_FORWARDING, AUTO_PORT_FORWARDING, RELAY
    }

    public enum State {
        UNDEFINED,
        PEER_CREATION_FAILED,
        DISCOVERY_STARTED,
        DISCOVERY_DIRECT_SUCCEEDED,
        DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED,
        DISCOVERY_FAILED,
        DISCOVERY_AUTO_PORT_FORWARDING_STARTED,
        DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED,
        DISCOVERY_AUTO_PORT_FORWARDING_FAILED,
        RELAY_STARTED,
        RELAY_SUCCEEDED,
        RELAY_FAILED,
        BOOT_STRAP_SUCCEEDED,
        BOOT_STRAP_FAILED;

        private String message;

        State() {
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    private final int port;
    private final boolean useManualPortForwarding;
    private final String networkInterface;
    private BootstrapNodes bootstrapNodes;
    private final Preferences preferences;

    private final SettableFuture<PeerDHT> settableFuture = SettableFuture.create();
    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.UNDEFINED);
    private final ObjectProperty<ConnectionType> connectionType = new SimpleObjectProperty<>(ConnectionType.UNDEFINED);

    private KeyPair keyPair;
    private Peer peer;
    private PeerDHT peerDHT;
    private Executor executor;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BootstrappedPeerBuilder(@Named(Node.PORT_KEY) int port,
                                   @Named(USE_MANUAL_PORT_FORWARDING_KEY) boolean useManualPortForwarding,
                                   @Named(NETWORK_INTERFACE_KEY) String networkInterface,
                                   BootstrapNodes bootstrapNodes,
                                   Preferences preferences) {
        this.port = port;
        this.useManualPortForwarding = useManualPortForwarding;
        this.networkInterface = networkInterface;
        this.bootstrapNodes = bootstrapNodes;
        this.preferences = preferences;
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

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public SettableFuture<PeerDHT> start() {
        try {
            DefaultEventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(20);
            ChannelClientConfiguration clientConf = PeerBuilder.createDefaultChannelClientConfiguration();
            clientConf.pipelineFilter(new PeerBuilder.EventExecutorGroupFilter(eventExecutorGroup));

            ChannelServerConfiguration serverConf = PeerBuilder.createDefaultChannelServerConfiguration();
            serverConf.pipelineFilter(new PeerBuilder.EventExecutorGroupFilter(eventExecutorGroup));
            serverConf.connectionTimeoutTCPMillis(5000);

            Bindings bindings = new Bindings();
            if (!NETWORK_INTERFACE_UNSPECIFIED.equals(networkInterface))
                bindings.addInterface(networkInterface);

            if (useManualPortForwarding) {
                peer = new PeerBuilder(keyPair)
                        .p2pId(bootstrapNodes.getP2pId())
                        .channelClientConfiguration(clientConf)
                        .channelServerConfiguration(serverConf)
                        .ports(port)
                        .bindings(bindings)
                        .tcpPortForwarding(port)
                        .udpPortForwarding(port)
                        .start();
            }
            else {
                peer = new PeerBuilder(keyPair)
                        .p2pId(bootstrapNodes.getP2pId())
                        .channelClientConfiguration(clientConf)
                        .channelServerConfiguration(serverConf)
                        .ports(port)
                        .bindings(bindings)
                        .start();
            }

            peerDHT = new PeerBuilderDHT(peer).start();

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified) {
                    if (verified)
                        log.debug("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
                    else
                        log.trace("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
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
            if (preferences.getUseUPnP())
                discoverExternalAddressUsingUPnP();
            else
                discoverExternalAddress();
        } catch (IOException e) {
            handleError(State.PEER_CREATION_FAILED, "Cannot create a peer with port: " +
                    port + ". Exception: " + e);
        }

        return settableFuture;
    }

    public void shutDown() {
        if (peerDHT != null)
            peerDHT.shutdown();
    }

    // We need to discover our external address and test if we are reachable for other nodes
    // We know our internal address from a discovery of our local network interfaces
    // We start a discover process with our bootstrap node.
    // There are 4 cases:
    // 1. If we are not behind a NAT we get reported back the same address as our internal.
    // 2. If we are behind a NAT and manual port forwarding is setup we get reported our external address from the
    // bootstrap node and the bootstrap node could ping us so we know we are reachable.
    // 3. If we are behind a NAT and the ping probes fails we need to setup port forwarding with UPnP or NAT-PMP.
    // If that is successfully setup we need to try again a discover so we find out our external address and have
    // tested successfully our reachability (the additional discover is done internally from startSetupPortforwarding)
    // 4. If the port forwarding failed we can try as last resort to open a permanent TCP connection to the
    // bootstrap node and use that peer as relay

    private void discoverExternalAddressUsingUPnP() {
        Node randomNode = bootstrapNodes.getRandomDiscoverNode();
        log.info("Random Node for discovering own address visible form outside: " + randomNode);
        FutureDiscover futureDiscover = peer.discover().peerAddress(randomNode.toPeerAddress()).start();
        setState(State.DISCOVERY_STARTED);
        PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
        FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
        FutureRelayNAT futureRelayNAT = peerNAT.startRelay(new TCPRelayClientConfig(), futureDiscover, futureNAT);
        futureRelayNAT.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture futureRelayNAT) throws Exception {
                if (futureDiscover.isSuccess()) {
                    if (useManualPortForwarding) {
                        setState(State.DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED,
                                "NAT traversal successful with manual port forwarding.");
                        setConnectionType(ConnectionType.MANUAL_PORT_FORWARDING);
                        bootstrap();
                    }
                    else {
                        setState(State.DISCOVERY_DIRECT_SUCCEEDED, "Visible to the network. No NAT traversal needed.");
                        setConnectionType(ConnectionType.DIRECT);
                        bootstrap();
                    }
                }
                else {
                    setState(State.DISCOVERY_AUTO_PORT_FORWARDING_STARTED);
                    if (futureNAT.isSuccess()) {
                        setState(State.DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED,
                                "NAT traversal successful with automatic port forwarding.");
                        setConnectionType(ConnectionType.AUTO_PORT_FORWARDING);
                        bootstrap();
                    }
                    else {
                        if (futureRelayNAT.isSuccess()) {
                            // relay mode succeeded
                            setState(State.RELAY_SUCCEEDED, "NAT traversal not successful. Using relay mode.");
                            setConnectionType(ConnectionType.RELAY);
                            bootstrap();
                        }
                        else {
                            // All attempts failed. Give up...
                            handleError(State.RELAY_FAILED, "NAT traversal using relay mode failed " + futureRelayNAT.failedReason());
                        }
                    }
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(State.RELAY_FAILED, "Exception at bootstrap: " + t.getMessage());
            }
        });
    }

    private void discoverExternalAddress() {
        Node randomNode = bootstrapNodes.getRandomDiscoverNode();
        log.info("Random Node for discovering own address visible form outside: " + randomNode);
        FutureDiscover futureDiscover = peer.discover().peerAddress(randomNode.toPeerAddress()).start();
        setState(State.DISCOVERY_STARTED);
        PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
        FutureRelayNAT futureRelayNAT = peerNAT.startRelay(new TCPRelayClientConfig(), futureDiscover);
        futureRelayNAT.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture futureRelayNAT) throws Exception {
                if (futureDiscover.isSuccess()) {
                    if (useManualPortForwarding) {
                        setState(State.DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED,
                                "NAT traversal successful with manual port forwarding.");
                        setConnectionType(ConnectionType.MANUAL_PORT_FORWARDING);
                        bootstrap();
                    }
                    else {
                        setState(State.DISCOVERY_DIRECT_SUCCEEDED, "Visible to the network. No NAT traversal needed.");
                        setConnectionType(ConnectionType.DIRECT);
                        bootstrap();
                    }
                }
                else {
                    if (futureRelayNAT.isSuccess()) {
                        // relay mode succeeded
                        setState(State.RELAY_SUCCEEDED, "Using relay mode.");
                        setConnectionType(ConnectionType.RELAY);
                        bootstrap();
                    }
                    else {
                        // All attempts failed. Give up...
                        handleError(State.RELAY_FAILED, "NAT traversal using relay mode failed " + futureRelayNAT.failedReason());
                    }
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(State.RELAY_FAILED, "Exception at bootstrap: " + t.getMessage());
            }
        });
    }

    private void bootstrap() {
        log.trace("start bootstrap");

        // We don't wait until bootstrap is done for speeding up startup process
        FutureBootstrap futureBootstrap = peer.bootstrap().bootstrapTo(bootstrapNodes.getBootstrapPeerAddresses()).start();
        futureBootstrap.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (futureBootstrap.isSuccess()) {
                    log.trace("bootstrap complete");
                    setState(State.BOOT_STRAP_SUCCEEDED, "Bootstrap was successful.");
                    settableFuture.set(peerDHT);
                }
                else {
                    handleError(State.BOOT_STRAP_FAILED, "Bootstrap failed. " +
                            futureBootstrap.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(State.BOOT_STRAP_FAILED, "Exception at bootstrap: " + t.getMessage());
            }
        });
    }

    public ConnectionType getConnectionType() {
        return connectionType.get();
    }

    public ReadOnlyObjectProperty<ConnectionType> connectionTypeProperty() {
        return connectionType;
    }

    private void setConnectionType(ConnectionType discoveryState) {
        this.connectionType.set(discoveryState);
    }

    public ObjectProperty<State> getState() {
        return state;
    }

    private void setState(State state) {
        setState(state, "", true);
    }

    private void setState(State state, String message) {
        setState(state, message, true);
    }

    private void setState(State state, String message, boolean isSuccess) {
        if (isSuccess)
            log.info(message);
        else
            log.error(message);

        state.setMessage(message);
        this.state.set(state);
    }

    private void handleError(State state, String errorMessage) {
        setState(state, errorMessage, false);
        peerDHT.shutdown();
        settableFuture.setException(new Exception(errorMessage));
    }
}
