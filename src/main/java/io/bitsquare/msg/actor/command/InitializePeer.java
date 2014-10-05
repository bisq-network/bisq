package io.bitsquare.msg.actor.command;


import java.util.Collection;

import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;

/**
 * <p>Command to initialize TomP2P Peer.</p>
 */
public class InitializePeer {

    private final Number160 peerId;
    private final Integer port;
    private final Collection<PeerAddress> bootstrapPeers;

    public InitializePeer(Number160 peerId, Integer port, Collection<PeerAddress> bootstrapPeers) {
        this.peerId = peerId;
        this.port = port;
        this.bootstrapPeers = bootstrapPeers;
    }

    public Number160 getPeerId() {
        return peerId;
    }

    public Integer getPort() {
        return port;
    }

    public Collection<PeerAddress> getBootstrapPeers() {
        return bootstrapPeers;
    }
}
