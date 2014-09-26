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

package io.bitsquare.msg;

import io.bitsquare.persistence.Persistence;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import com.google.inject.name.Named;

import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.security.KeyPair;

import javax.annotation.concurrent.Immutable;

import javax.inject.Inject;

import net.tomp2p.connection.Ports;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageLayer;
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
import net.tomp2p.peers.PeerStatatistic;
import net.tomp2p.relay.DistributedRelay;
import net.tomp2p.relay.FutureRelay;
import net.tomp2p.storage.Storage;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.msg.SeedNodeAddress.StaticSeedNodeAddresses;

/**
 * Creates a DHT peer and bootstrap to a seed node
 */
@Immutable
public class BootstrappedPeerFactory {
    private static final Logger log = LoggerFactory.getLogger(BootstrappedPeerFactory.class);

    private KeyPair keyPair;
    private Storage storage;
    private final SeedNodeAddress seedNodeAddress;
    private final Persistence persistence;

    private final SettableFuture<PeerDHT> settableFuture = SettableFuture.create();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BootstrappedPeerFactory(Persistence persistence,
                                   @Named("defaultSeedNode") StaticSeedNodeAddresses defaultStaticSeedNodeAddresses) {
        this.persistence = persistence;
        this.seedNodeAddress = new SeedNodeAddress(defaultStaticSeedNodeAddresses);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setKeyPair(@NotNull KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public void setStorage(@NotNull Storage storage) {
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ListenableFuture<PeerDHT> start() {
        try {
            int randomPort = new Ports().tcpPort();
            Peer peer = new PeerBuilder(keyPair).ports(randomPort).start();
            PeerDHT peerDHT = new PeerBuilderDHT(peer).storageLayer(new StorageLayer(storage)).start();

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified) {
                    log.debug("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatatistic peerStatistics) {
                    log.debug("Peer removed: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatatistic peerStatistics) {
                }
            });

            // We save last successful bootstrap method.
            // Reset it to "default" after 5 start ups.
            Object lastSuccessfulBootstrapCounterObject = persistence.read(this, "lastSuccessfulBootstrapCounter");
            int lastSuccessfulBootstrapCounter = 0;
            if (lastSuccessfulBootstrapCounterObject != null)
                lastSuccessfulBootstrapCounter = (int) lastSuccessfulBootstrapCounterObject;

            if (lastSuccessfulBootstrapCounter > 5)
                persistence.write(this, "lastSuccessfulBootstrap", "default");

            persistence.write(this, "lastSuccessfulBootstrapCounter", lastSuccessfulBootstrapCounter + 1);


            String lastSuccessfulBootstrap = (String) persistence.read(this, "lastSuccessfulBootstrap");
            if (lastSuccessfulBootstrap == null)
                lastSuccessfulBootstrap = "default";

            log.debug("lastSuccessfulBootstrap = " + lastSuccessfulBootstrap);
            switch (lastSuccessfulBootstrap) {
                case "relay":
                    PeerNAT nodeBehindNat = new PeerBuilderNAT(peerDHT.peer()).start();
                    bootstrapWithRelay(peerDHT, nodeBehindNat);
                    break;
                case "startPortForwarding":
                    FutureDiscover futureDiscover =
                            peerDHT.peer().discover().peerAddress(getBootstrapAddress()).start();
                    bootstrapWithPortForwarding(peerDHT, futureDiscover);
                    break;
                case "default":
                default:
                    bootstrap(peerDHT);
                    break;
            }
        } catch (IOException e) {
            log.error("Exception: " + e);
            settableFuture.setException(e);
        }

        return settableFuture;
    }

    private PeerAddress getBootstrapAddress() {
        try {
            return new PeerAddress(Number160.createHash(seedNodeAddress.getId()),
                    InetAddress.getByName(seedNodeAddress.getIp()),
                    seedNodeAddress.getPort(),
                    seedNodeAddress.getPort());
        } catch (UnknownHostException e) {
            log.error("getBootstrapAddress failed: " + e.getMessage());
            return null;
        }
    }

    private void bootstrap(PeerDHT peerDHT) {
        // Check if peer is reachable from outside
        FutureDiscover futureDiscover = peerDHT.peer().discover().peerAddress(getBootstrapAddress()).start();
        BootstrappedPeerFactory ref = this;
        futureDiscover.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    // We are not behind a NAT and reachable to other peers
                    log.debug("We are not behind a NAT and reachable to other peers: My address visible to the " +
                            "outside is " + futureDiscover.peerAddress());
                    requestBootstrapPeerMap();
                    settableFuture.set(peerDHT);

                    persistence.write(ref, "lastSuccessfulBootstrap", "default");
                }
                else {
                    log.warn("Discover has failed. Reason: " + futureDiscover.failedReason());
                    log.warn("We are probably behind a NAT and not reachable to other peers. We try port forwarding " +
                            "as next step.");

                    bootstrapWithPortForwarding(peerDHT, futureDiscover);
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                log.error("Exception at discover: " + t);
                settableFuture.setException(t);
            }
        });
    }

    private void bootstrapWithPortForwarding(PeerDHT peerDHT, FutureDiscover futureDiscover) {
        // Assume we are behind a NAT device
        PeerNAT nodeBehindNat = new PeerBuilderNAT(peerDHT.peer()).start();

        // Try to set up port forwarding with UPNP and NATPMP if peer is not reachable
        FutureNAT futureNAT = nodeBehindNat.startSetupPortforwarding(futureDiscover);
        BootstrappedPeerFactory ref = this;
        futureNAT.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    // Port forwarding has succeed
                    log.debug("Port forwarding was successful. My address visible to the outside is " +
                            futureNAT.peerAddress());
                    requestBootstrapPeerMap();
                    settableFuture.set(peerDHT);

                    persistence.write(ref, "lastSuccessfulBootstrap", "portForwarding");
                }
                else {
                    log.warn("Port forwarding has failed. Reason: " + futureNAT.failedReason());
                    log.warn("We try to use a relay as next step.");

                    bootstrapWithRelay(peerDHT, nodeBehindNat);
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                log.error("Exception at port forwarding: " + t);
                settableFuture.setException(t);
            }
        });
    }

    private void bootstrapWithRelay(PeerDHT peerDHT, PeerNAT nodeBehindNat) {
        // Last resort: we try to use other peers as relays

        // The firewalled flags have to be set, so that other peers don’t add the unreachable peer to their peer maps.
        Peer peer = peerDHT.peer();
        PeerAddress serverPeerAddress = peer.peerBean().serverPeerAddress();
        serverPeerAddress = serverPeerAddress.changeFirewalledTCP(true).changeFirewalledUDP(true);
        peer.peerBean().serverPeerAddress(serverPeerAddress);

        // Find neighbors
        FutureBootstrap futureBootstrap = peer.bootstrap().peerAddress(getBootstrapAddress()).start();
        futureBootstrap.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("Bootstrap was successful. bootstrapTo  = " + futureBootstrap.bootstrapTo());

                    setupRelay(peerDHT, nodeBehindNat, getBootstrapAddress());
                }
                else {
                    log.error("Bootstrap failed. Reason:" + futureBootstrap.failedReason());
                    settableFuture.setException(new Exception(futureBootstrap.failedReason()));
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                log.error("Exception at bootstrap: " + t);
                settableFuture.setException(t);
            }
        });
    }

    private void setupRelay(PeerDHT peerDHT, PeerNAT nodeBehindNat, PeerAddress bootstrapAddress) {
        FutureRelay futureRelay = new FutureRelay();
        futureRelay.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("Start setup relay was successful.");
                    futureRelay.relays().forEach(e -> log.debug("remotePeer = " + e.remotePeer()));

                    findNeighbors2(peerDHT, bootstrapAddress);
                }
                else {
                    log.error("setupRelay failed. Reason: " + futureRelay.failedReason());
                    log.error("Bootstrap failed. We give up...");
                    settableFuture.setException(new Exception(futureRelay.failedReason()));
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                log.error("Exception at setup relay: " + t);
            }
        });

        DistributedRelay distributedRelay = nodeBehindNat.startSetupRelay(futureRelay);
        distributedRelay.addRelayListener((distributedRelay1, peerConnection) -> {
            log.error("startSetupRelay Failed");
            settableFuture.setException(new Exception("startSetupRelay Failed"));
        });
    }

    private void findNeighbors2(PeerDHT peerDHT, PeerAddress bootstrapAddress) {
        // find neighbors again
        FutureBootstrap futureBootstrap2 = peerDHT.peer().bootstrap().peerAddress(bootstrapAddress).start();
        BootstrappedPeerFactory ref = this;
        futureBootstrap2.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("Final bootstrap was successful. bootstrapTo  = " + futureBootstrap2.bootstrapTo());
                    requestBootstrapPeerMap();
                    settableFuture.set(peerDHT);

                    persistence.write(ref, "lastSuccessfulBootstrap", "relay");
                }
                else {
                    log.error("Bootstrap 2 failed. Reason:" + futureBootstrap2.failedReason());
                    log.error("We give up...");
                    settableFuture.setException(new Exception(futureBootstrap2.failedReason()));
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                log.error("Exception at bootstrap 2: " + t);
                settableFuture.setException(t);
            }
        });
    }

    // TODO we want to get a list of connected nodes form the seed node and save them locally for future bootstrapping
    // The seed node should only be used if no other known peers are available
    private void requestBootstrapPeerMap() {
        log.debug("getBootstrapPeerMap");
    }
}
