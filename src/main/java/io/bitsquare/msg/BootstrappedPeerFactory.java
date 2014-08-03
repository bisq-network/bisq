package io.bitsquare.msg;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyPair;
import javax.annotation.concurrent.Immutable;
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

/**
 * Creates a DHT peer and bootstrap to a seed node
 */
@Immutable
public class BootstrappedPeerFactory
{
    private static final Logger log = LoggerFactory.getLogger(BootstrappedPeerFactory.class);

    private final KeyPair keyPair;
    private final Storage storage;
    private final SeedNodeAddress seedNodeAddress;

    private final SettableFuture<PeerDHT> settableFuture = SettableFuture.create();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * We use localhost as default seed node
     */
    public BootstrappedPeerFactory(@NotNull KeyPair keyPair, @NotNull Storage storage)
    {
        this(keyPair, storage, new SeedNodeAddress(SeedNodeAddress.StaticSeedNodeAddresses.LOCALHOST));
    }

    public BootstrappedPeerFactory(@NotNull KeyPair keyPair, @NotNull Storage storage, @NotNull SeedNodeAddress seedNodeAddress)
    {
        this.keyPair = keyPair;
        this.storage = storage;
        this.seedNodeAddress = seedNodeAddress;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ListenableFuture<PeerDHT> start()
    {
        try
        {
            int randomPort = new Ports().tcpPort();
            Peer peer = new PeerBuilder(keyPair).ports(randomPort).start();
            PeerDHT peerDHT = new PeerBuilderDHT(peer).storageLayer(new StorageLayer(storage)).start();

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener()
            {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified)
                {
                    log.debug("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);

                  /*  NavigableSet<PeerAddress> closePeers = peer.peerBean().peerMap().closePeers(2);
                    log.debug("closePeers size  = " + closePeers.size());
                    log.debug("closePeers  = " + closePeers);
                    closePeers.forEach(e -> log.debug("forEach: " + e.toString()));

                    List<PeerAddress> allPeers = peer.peerBean().peerMap().all();
                    log.debug("allPeers size  = " + allPeers.size());
                    log.debug("allPeers  = " + allPeers);
                    allPeers.forEach(e -> log.debug("forEach: " + e.toString()));*/
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatatistic peerStatistics)
                {
                    log.debug("Peer removed: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatatistic peerStatistics)
                {
                    // log.debug("Peer updated: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }
            });

            discover(peerDHT);
        } catch (IOException e)
        {
            log.error("Exception: " + e);
            settableFuture.setException(e);
        }

        return settableFuture;
    }

    private void discover(PeerDHT peerDHT)
    {
        try
        {
            PeerAddress bootstrapAddress = new PeerAddress(Number160.createHash(seedNodeAddress.getId()),
                                                           InetAddress.getByName(seedNodeAddress.getIp()),
                                                           seedNodeAddress.getPort(),
                                                           seedNodeAddress.getPort());
            // Check if peer is reachable from outside
            FutureDiscover futureDiscover = peerDHT.peer().discover().peerAddress(bootstrapAddress).start();
            futureDiscover.addListener(new BaseFutureListener<BaseFuture>()
            {
                @Override
                public void operationComplete(BaseFuture future) throws Exception
                {
                    if (future.isSuccess())
                    {
                        // We are not behind a NAT and reachable to other peers
                        log.debug("We are not behind a NAT and reachable to other peers: My address visible to the outside is " + futureDiscover.peerAddress());
                        getBootstrapPeerMap();
                        settableFuture.set(peerDHT);
                    }
                    else
                    {
                        log.warn("Discover has failed. Reason: " + futureDiscover.failedReason());
                        log.warn("We are probably behind a NAT and not reachable to other peers. We try port forwarding as next step.");

                        startPortForwarding(peerDHT, bootstrapAddress, futureDiscover);
                    }
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception
                {
                    log.error("Exception at discover: " + t);
                    settableFuture.setException(t);
                }
            });
        } catch (IOException e)
        {
            log.error("Exception: " + e);
            settableFuture.setException(e);
        }
    }

    private void startPortForwarding(PeerDHT peerDHT, PeerAddress bootstrapAddress, FutureDiscover futureDiscover)
    {
        // Assume we are behind a NAT device
        PeerNAT nodeBehindNat = new PeerBuilderNAT(peerDHT.peer()).start();

        // Try to set up port forwarding with UPNP and NATPMP if peer is not reachable
        FutureNAT futureNAT = nodeBehindNat.startSetupPortforwarding(futureDiscover);
        futureNAT.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                if (future.isSuccess())
                {
                    // Port forwarding has succeed
                    log.debug("Port forwarding was successful. My address visible to the outside is " + futureNAT.peerAddress());
                    getBootstrapPeerMap();
                    settableFuture.set(peerDHT);
                }
                else
                {
                    log.warn("Port forwarding has failed. Reason: " + futureNAT.failedReason());
                    log.warn("We try to use a relay as next step.");

                    prepareRelay(peerDHT, nodeBehindNat, bootstrapAddress);
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                log.error("Exception at port forwarding: " + t);
                settableFuture.setException(t);
            }
        });
    }

    private void prepareRelay(PeerDHT peerDHT, PeerNAT nodeBehindNat, PeerAddress bootstrapAddress)
    {
        // Last resort: we try to use other peers as relays

        // The firewalled flags have to be set, so that other peers don’t add the unreachable peer to their peer maps.
        Peer peer = peerDHT.peer();
        PeerAddress serverPeerAddress = peer.peerBean().serverPeerAddress();
        serverPeerAddress = serverPeerAddress.changeFirewalledTCP(true).changeFirewalledUDP(true);
        peer.peerBean().serverPeerAddress(serverPeerAddress);

        // Find neighbors
        FutureBootstrap futureBootstrap = peer.bootstrap().peerAddress(bootstrapAddress).start();
        futureBootstrap.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                if (future.isSuccess())
                {
                    log.debug("Bootstrap was successful. bootstrapTo  = " + futureBootstrap.bootstrapTo());

                    setupRelay(peerDHT, nodeBehindNat, bootstrapAddress);
                }
                else
                {
                    log.error("Bootstrap failed. Reason:" + futureBootstrap.failedReason());
                    settableFuture.setException(new Exception(futureBootstrap.failedReason()));
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                log.error("Exception at bootstrap: " + t);
                settableFuture.setException(t);
            }
        });
    }

    private void setupRelay(PeerDHT peerDHT, PeerNAT nodeBehindNat, PeerAddress bootstrapAddress)
    {
        FutureRelay futureRelay = new FutureRelay();
        futureRelay.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                if (future.isSuccess())
                {
                    log.debug("Start setup relay was successful.");
                    futureRelay.relays().forEach(e -> log.debug("remotePeer = " + e.remotePeer()));

                    findNeighbors2(peerDHT, nodeBehindNat, bootstrapAddress);
                }
                else
                {
                    log.error("setupRelay failed. Reason: " + futureRelay.failedReason());
                    log.error("Bootstrap failed. We give up...");
                    settableFuture.setException(new Exception(futureRelay.failedReason()));
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                log.error("Exception at setup relay: " + t);
            }
        });

        DistributedRelay distributedRelay = nodeBehindNat.startSetupRelay(futureRelay);
        distributedRelay.addRelayListener((distributedRelay1, peerConnection) -> {
            log.error("startSetupRelay Failed");
            settableFuture.setException(new Exception("startSetupRelay Failed"));
        });
    }

    private void findNeighbors2(PeerDHT peerDHT, PeerNAT nodeBehindNat, PeerAddress bootstrapAddress)
    {
        // find neighbors again
        FutureBootstrap futureBootstrap2 = peerDHT.peer().bootstrap().peerAddress(bootstrapAddress).start();
        futureBootstrap2.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                if (future.isSuccess())
                {
                    log.debug("Final bootstrap was successful. bootstrapTo  = " + futureBootstrap2.bootstrapTo());
                    getBootstrapPeerMap();
                    settableFuture.set(peerDHT);
                }
                else
                {
                    log.error("Bootstrap 2 failed. Reason:" + futureBootstrap2.failedReason());
                    log.error("We give up...");
                    settableFuture.setException(new Exception(futureBootstrap2.failedReason()));
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                log.error("Exception at bootstrap 2: " + t);
                settableFuture.setException(t);
            }
        });
    }

    // TODO we want to get a list of connected nodes form the seed node and save them locally for future bootstrapping
    // The seed node should only be used if no other known peers are available
    private void getBootstrapPeerMap()
    {
        log.debug("getBootstrapPeerMap");

      /*  NavigableSet<PeerAddress> closePeers = peer.peerBean().peerMap().closePeers(2);
        log.debug("closePeers size  = " + closePeers.size());
        log.debug("closePeers  = " + closePeers);
        closePeers.forEach(e -> log.debug("forEach: " + e.toString()));

        List<PeerAddress> allPeers = peer.peerBean().peerMap().all();
        log.debug("allPeers size  = " + allPeers.size());
        log.debug("allPeers  = " + allPeers);
        allPeers.forEach(e -> log.debug("forEach: " + e.toString())); */
    }


}
