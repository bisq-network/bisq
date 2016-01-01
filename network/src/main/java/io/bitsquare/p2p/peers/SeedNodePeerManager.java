package io.bitsquare.p2p.peers;

import io.bitsquare.p2p.network.NetworkNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeedNodePeerManager extends PeerManager {
    private static final Logger log = LoggerFactory.getLogger(SeedNodePeerManager.class);

    public SeedNodePeerManager(NetworkNode networkNode) {
        super(networkNode);
    }
}
