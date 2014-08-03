package io.bitsquare;

import io.bitsquare.msg.SeedNodeAddress;
import java.util.List;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatatistic;
import net.tomp2p.relay.FutureRelay;
import net.tomp2p.relay.RelayRPC;
import net.tomp2p.tracker.PeerBuilderTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Well known node which is reachable for all peers for bootstrapping.
 * There will be several SeedNodes running on several servers.
 * <p>
 * TODO: Alternative bootstrap methods will follow later (save locally list of known nodes reported form other peers,...)
 */
public class SeedNode
{
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);

    private static final List<SeedNodeAddress.StaticSeedNodeAddresses> staticSedNodeAddresses = SeedNodeAddress.StaticSeedNodeAddresses.getAllSeedNodeAddresses();

    /**
     * @param args If no args passed we use localhost, otherwise the param is used as index for selecting an address from seedNodeAddresses
     * @throws Exception
     */
    public static void main(String[] args)
    {
        int index = 0;
        SeedNode seedNode = new SeedNode();
        if (args.length > 0)
        {
            // use host index passes as param
            int param = Integer.valueOf(args[0]);
            if (param < staticSedNodeAddresses.size())
                index = param;
        }
        try
        {
            seedNode.startupUsingAddress(new SeedNodeAddress(staticSedNodeAddresses.get(index)));
        } catch (Exception e)
        {
            e.printStackTrace();
            log.error(e.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    public SeedNode()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void startupUsingAddress(SeedNodeAddress seedNodeAddress)
    {
        try
        {
            Peer peer = new PeerBuilder(Number160.createHash(seedNodeAddress.getId())).ports(seedNodeAddress.getPort()).start();

            // Need to add all features the clients will use (otherwise msg type is UNKNOWN_ID)
            new PeerBuilderDHT(peer).start();
            PeerNAT nodeBehindNat = new PeerBuilderNAT(peer).start();
            new RelayRPC(peer);
            new PeerBuilderTracker(peer);
            nodeBehindNat.startSetupRelay(new FutureRelay());

            log.debug("Peer started. " + peer.peerAddress());

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener()
            {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified)
                {
                    log.debug("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatatistic peerStatistics)
                {
                    log.debug("Peer removed: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatatistic peerStatistics)
                {
                    log.debug("Peer updated: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }
            });

            // We keep server in endless loop
            for (; ; )
            {
                // Optional pinging
                boolean pingPeers = false;
                if (pingPeers)
                {
                    for (PeerAddress peerAddress : peer.peerBean().peerMap().all())
                    {
                        BaseFuture future = peer.ping().peerAddress(peerAddress).tcpPing().start();
                        future.addListener(new BaseFutureListener<BaseFuture>()
                        {
                            @Override
                            public void operationComplete(BaseFuture future) throws Exception
                            {
                                if (future.isSuccess())
                                {
                                    log.debug("peer online (TCP):" + peerAddress);
                                }
                                else
                                {
                                    log.debug("offline " + peerAddress);
                                }
                            }

                            @Override
                            public void exceptionCaught(Throwable t) throws Exception
                            {
                                log.error("exceptionCaught " + t);
                            }
                        });

                        future = peer.ping().peerAddress(peerAddress).start();
                        future.addListener(new BaseFutureListener<BaseFuture>()
                        {
                            @Override
                            public void operationComplete(BaseFuture future) throws Exception
                            {
                                if (future.isSuccess())
                                {
                                    log.debug("peer online (UDP):" + peerAddress);
                                }
                                else
                                {
                                    log.debug("offline " + peerAddress);
                                }
                            }

                            @Override
                            public void exceptionCaught(Throwable t) throws Exception
                            {
                                log.error("exceptionCaught " + t);
                            }
                        });
                    }
                    Thread.sleep(1500);
                }
            }
        } catch (Exception e)
        {
            log.error("Exception: " + e);
        }
    }

}
