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

import com.google.common.util.concurrent.SettableFuture;

import com.google.inject.name.Named;

import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.security.KeyPair;

import javax.inject.Inject;

import javafx.beans.property.ObjectProperty;
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
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatistic;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.DefaultEventExecutorGroup;


/**
 * Creates a DHT peer and bootstraps to the network via a bootstrap node
 */
class BootstrappedPeerFactory {
    private static final Logger log = LoggerFactory.getLogger(BootstrappedPeerFactory.class);

    static final String BOOTSTRAP_NODE_KEY = "bootstrapNode";
    static final String NETWORK_INTERFACE_KEY = "interface";
    static final String NETWORK_INTERFACE_UNSPECIFIED = "<unspecified>";
    static final String USE_MANUAL_PORT_FORWARDING_KEY = "node.useManualPortForwarding";

    private KeyPair keyPair;
    private final int port;
    private boolean useManualPortForwarding;
    private final Node bootstrapNode;
    private final String networkInterface;

    private final SettableFuture<PeerDHT> settableFuture = SettableFuture.create();

    private final ObjectProperty<BootstrapState> bootstrapState = new SimpleObjectProperty<>();

    private Peer peer;
    private PeerDHT peerDHT;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BootstrappedPeerFactory(@Named(Node.PORT_KEY) int port,
                                   @Named(USE_MANUAL_PORT_FORWARDING_KEY) boolean useManualPortForwarding,
                                   @Named(BOOTSTRAP_NODE_KEY) Node bootstrapNode,
                                   @Named(NETWORK_INTERFACE_KEY) String networkInterface) {
        this.port = port;
        this.useManualPortForwarding = useManualPortForwarding;
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
            DefaultEventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(250);
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

            discoverExternalAddress();
        } catch (IOException e) {
            handleError(BootstrapState.PEER_CREATION_FAILED, "Cannot create a peer with port: " +
                    port + ". Exception: " + e);
        }

        return settableFuture;
    }

    void shutDown() {
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
    // bootstrap node and use that peer as relay (currently not supported as its too unstable)

    private void discoverExternalAddress() {
        FutureDiscover futureDiscover = peer.discover().peerAddress(getBootstrapAddress()).start();
        setState(BootstrapState.DISCOVERY_STARTED, "We are starting discovery against a bootstrap node.");
        PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
        FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
        futureNAT.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                // If futureDiscover was successful we are directly connected (or manual port forwarding is set)
                if (futureDiscover.isSuccess()) {
                    if (useManualPortForwarding) {
                        setState(BootstrapState.DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED,
                                "We use manual port forwarding and are visible to other peers.");
                        bootstrap();
                    }
                    else {
                        setState(BootstrapState.DISCOVERY_DIRECT_SUCCEEDED,
                                "We are not behind a NAT and visible to other peers.");
                        bootstrap();
                    }
                }
                else {
                    setState(BootstrapState.DISCOVERY_AUTO_PORT_FORWARDING_STARTED,
                            "We are probably behind a NAT and not reachable to other peers. " +
                                    "We try to setup automatic port forwarding.");
                    if (futureNAT.isSuccess()) {
                        setState(BootstrapState.DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED,
                                "Discover with automatic port forwarding was successful.");
                        bootstrap();
                    }
                    else {
                        handleError(BootstrapState.DISCOVERY_AUTO_PORT_FORWARDING_FAILED, "Automatic port forwarding " +
                                "failed. " +
                                "Fail reason: " + future.failedReason() +
                                "\nCheck if UPnP is not enabled on your router. " +
                                "\nYou can try also to setup manual port forwarding. " +
                                "\nRelay mode is currently not supported but will follow later. ");

                        // For the moment we don't support relay mode as it has too much problems
                  /*  setState(BootstrapState.AUTO_PORT_FORWARDING_NOT_SUCCEEDED, "Port forwarding has failed. " +
                            "We try to use a relay as next step.");
                    bootstrapWithRelay();*/
                    }
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(BootstrapState.DISCOVERY_FAILED, "Exception at discover visibility: " + t
                        .getMessage());
            }
        });
    }

    // For the moment we don't support relay mode as it has too much problems
    // 3. Attempt: We try to use another peer as relay
   /* private void bootstrapWithRelay() {
        setState(BootstrapState.RELAY_INIT, "We try to use another peer as relay.");
        FutureDiscover futureDiscover = peer.discover().peerAddress(getBootstrapAddress()).start();
        PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
        FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
        FutureRelayNAT futureRelayNAT = peerNAT.startRelay(RelayConfig.OpenTCP(), futureDiscover, futureNAT);
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
    }*/

    private void bootstrap() {
        FutureBootstrap futureBootstrap = peer.bootstrap().peerAddress(getBootstrapAddress()).start();
        futureBootstrap.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (futureBootstrap.isSuccess()) {
                    settableFuture.set(peerDHT);
                }
                else {
                    handleError(BootstrapState.BOOT_STRAP_FAILED, "Bootstrapping failed. " +
                            futureBootstrap.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                handleError(BootstrapState.BOOT_STRAP_FAILED, "Exception at bootstrap: " + t.getMessage());
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

    public Node getBootstrapNode() {
        return bootstrapNode;
    }

    public ObjectProperty<BootstrapState> getBootstrapState() {
        return bootstrapState;
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
        this.bootstrapState.set(bootstrapState);
    }

    private void handleError(BootstrapState state, String errorMessage) {
        setState(state, errorMessage, false);
        peerDHT.shutdown();
        settableFuture.setException(new Exception(errorMessage));
    }
}
