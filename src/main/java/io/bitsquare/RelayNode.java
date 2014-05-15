package io.bitsquare;

import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;

public class RelayNode
{
    private static Peer masterPeer = null;
    public static Number160 ID = Number160.createHash(1);

    public static void main(String[] args) throws Exception
    {
        if (args.length == 1)
            INSTANCE(new Integer(args[0]));
        else
            INSTANCE(5000);
    }

    public static Peer INSTANCE(int port) throws Exception
    {
        if (masterPeer == null)
        {
            masterPeer = new PeerMaker(ID).setPorts(port).makeAndListen();
            // masterPeer = new PeerMaker(ID).setPorts(port).setBagSize(100).makeAndListen();     // setBagSize cause sync problems...
            masterPeer.getBroadcastRPC().getConnectionBean().getConnectionReservation().reserve(10).awaitUninterruptibly();
        }
        return masterPeer;
    }
}
