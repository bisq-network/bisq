package io.bitsquare.msg;

import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;

public class BootstrapMasterPeer
{
    private static Peer masterPeer = null;
    public static Number160 ID = Number160.createHash(1);

    public static void main(String[] args) throws Exception
    {
        INSTANCE(5000);
    }

    public static Peer INSTANCE(int port) throws Exception
    {
        if (masterPeer == null)
            masterPeer = new PeerMaker(ID).setPorts(port).makeAndListen();

        return masterPeer;
    }
}
