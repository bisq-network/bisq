package io.bitsquare.p2p.peers;

import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.storage.P2PDataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SeedNodeRequestDataManager extends RequestDataManager {
    private static final Logger log = LoggerFactory.getLogger(SeedNodeRequestDataManager.class);

    public SeedNodeRequestDataManager(NetworkNode networkNode, P2PDataStorage dataStorage, PeerManager peerManager, Listener listener) {
        super(networkNode, dataStorage, peerManager, listener);
    }

    @Override
    public void onPeerAuthenticated(Address peerAddress, Connection connection) {
        //TODO not clear which use case is handles here...
        if (dataStorage.getMap().isEmpty()) {
            UserThread.runAfterRandomDelay(()
                    -> requestDataFromAuthenticatedSeedNode(peerAddress, connection), 2, 5, TimeUnit.SECONDS);
        }
        super.onPeerAuthenticated(peerAddress, connection);
    }
}
