package io.bitsquare.p2p.peers;

import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.NetworkNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class SeedNodePeerManager extends PeerManager {
    private static final Logger log = LoggerFactory.getLogger(SeedNodePeerManager.class);


    public SeedNodePeerManager(NetworkNode networkNode) {
        super(networkNode, null);
    }

    public void authenticateToSeedNode() {
        Log.traceCall();
        checkArgument(seedNodeAddressesOptional.isPresent(),
                "seedNodeAddresses must be set before calling authenticateToSeedNode");
        checkArgument(!seedNodeAddressesOptional.get().isEmpty(),
                "seedNodeAddresses must not be empty");
        remainingSeedNodes.addAll(seedNodeAddressesOptional.get());
        NodeAddress peerNodeAddress = getAndRemoveRandomAddress(remainingSeedNodes);
        authenticateToFirstSeedNode(peerNodeAddress);

        startCheckSeedNodeConnectionTask();
    }

    @Override
    protected void createDbStorage(File storageDir) {
        // Do nothing. 
        // The seed node does not store persisted peers in the local db 
    }

    @Override
    protected void initPersistedPeers() {
        // Do nothing. 
        // The seed node does not store persisted peers in the local db 
    }

    @Override
    protected void onFirstSeedNodeAuthenticated() {
        // If we are seed node we want to first connect to all other seed nodes before connecting to the reported peers.
        authenticateToRemainingSeedNode();
    }

    @Override
    protected void onRemainingSeedNodeAuthenticated() {
        // If we are seed node we want to first connect to all other seed nodes before connecting to the reported peers.
        authenticateToRemainingSeedNode();
    }

    @Override
    protected void handleNoSeedNodesAvailableCase() {
        Log.traceCall();
        log.info("We don't have more seed nodes available. " +
                "We authenticate to reported peers and try again after a random pause with the seed nodes which failed or if " +
                "none available with the reported peers.");

        boolean reportedPeersAvailableCalled = false;
        if (reportedPeersAvailable()) {
            authenticateToRemainingReportedPeer();
            reportedPeersAvailableCalled = true;
        }

        resetRemainingSeedNodes();
        if (!remainingSeedNodes.isEmpty()) {
            if (authenticateToRemainingSeedNodeTimer == null)
                authenticateToRemainingSeedNodeTimer = UserThread.runAfterRandomDelay(() -> authenticateToRemainingSeedNode(),
                        10, 20, TimeUnit.SECONDS);
        } else if (!reportedPeersAvailableCalled) {
            if (authenticateToRemainingReportedPeerTimer == null)
                authenticateToRemainingReportedPeerTimer = UserThread.runAfterRandomDelay(() -> authenticateToRemainingReportedPeer(),
                        10, 20, TimeUnit.SECONDS);
        }
    }


}
