package io.bitsquare;

import java.io.IOException;
import javafx.application.Application;
import javafx.stage.Stage;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Relay extends Application
{
    private static final Logger log = LoggerFactory.getLogger(Relay.class);
    private static final Number160 ID = Number160.createHash(1);

    private static Peer masterPeer = null;
    private static int port;

    public static void main(String[] args)
    {
        if (args != null && args.length == 1)
        {
            port = new Integer(args[0]);
        }
        else
        {
            port = 5000;
        }

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException
    {
        log.trace("Startup: start");
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
