package io.bitsquare;

import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Network node for relaying p2p msg
 */
class RelayNode
{
    private static final Logger log = LoggerFactory.getLogger(RelayNode.class);
    private static final Number160 ID = Number160.createHash(1);

    private static Peer masterPeer = null;

    public static void main(String[] args) throws Exception
    {
        if (args != null && args.length == 1)
        {
            INSTANCE(new Integer(args[0]));
        }
        else
        {
            INSTANCE(5000);
        }
    }

    private static void INSTANCE(int port) throws Exception
    {
        if (masterPeer == null)
        {
            masterPeer = new PeerMaker(ID).setPorts(port).makeAndListen();
            // masterPeer = new PeerMaker(ID).setPorts(port).setBagSize(100).makeAndListen();     // setBagSize cause sync problems...
            masterPeer.getBroadcastRPC().getConnectionBean().getConnectionReservation().reserve(3).awaitUninterruptibly();
            masterPeer.getConnectionHandler().getPeerBean().getPeerMap().addPeerMapChangeListener(new PeerMapChangeListener()
            {
                @Override
                public void peerInserted(PeerAddress peerAddress)
                {
                    log.info("peerInserted " + peerAddress);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress)
                {
                    log.info("peerRemoved " + peerAddress);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress)
                {
                    log.info("peerUpdated " + peerAddress);
                }
            });
        }
    }
}
