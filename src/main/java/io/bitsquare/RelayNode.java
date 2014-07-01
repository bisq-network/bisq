package io.bitsquare;

import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;

/**
 * Network node for relaying p2p msg
 */
class RelayNode
{
    private static final Number160 ID = Number160.createHash(1);

    private static Peer masterPeer = null;

    public static void main(String[] args) throws Exception
    {
        if (args != null && args.length == 1)
            INSTANCE(new Integer(args[0]));
        else
            INSTANCE(5000);
    }

    private static void INSTANCE(int port) throws Exception
    {
        if (masterPeer == null)
        {
            masterPeer = new PeerMaker(ID).setPorts(port).makeAndListen();
            // masterPeer = new PeerMaker(ID).setPorts(port).setBagSize(100).makeAndListen();     // setBagSize cause sync problems...
            masterPeer.getBroadcastRPC().getConnectionBean().getConnectionReservation().reserve(3).awaitUninterruptibly();
        }
    }
}
