package io.bitsquare.msg.actor.event;


import net.tomp2p.peers.Number160;

/**
 * <p>TomP2P Peer Initialized event.</p>
 */
public class PeerInitialized {

    private final Number160 peerId;

    public PeerInitialized(Number160 peerId) {
        this.peerId = peerId;
    }

    public Number160 getPeerId() {
        return peerId;
    }

}
