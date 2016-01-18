package io.bitsquare.p2p;

import io.bitsquare.app.Log;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.RequestDataManager;
import io.bitsquare.p2p.peers.SeedNodePeerManager;
import io.bitsquare.p2p.peers.SeedNodeRequestDataManager;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class SeedNodeP2PService extends P2PService {
    private static final Logger log = LoggerFactory.getLogger(SeedNodeP2PService.class);

    public SeedNodeP2PService(SeedNodesRepository seedNodesRepository,
                              Address mySeedNodeAddress,
                              File torDir,
                              boolean useLocalhost,
                              int networkId,
                              File storageDir) {
        super(seedNodesRepository, mySeedNodeAddress.port, torDir, useLocalhost, networkId, storageDir, null, null);

        // we remove ourselves from the list of seed nodes
        seedNodeAddresses.remove(mySeedNodeAddress);
    }

    @Override
    protected PeerManager getNewPeerManager() {
        return new SeedNodePeerManager(networkNode);
    }

    @Override
    protected RequestDataManager getNewRequestDataManager() {
        return new SeedNodeRequestDataManager(networkNode, dataStorage, peerManager);
    }

    @Override
    protected MonadicBinding<Boolean> getNewReadyForAuthenticationBinding() {
        return EasyBind.combine(hiddenServicePublished, notAuthenticated,
                (hiddenServicePublished, notAuthenticated) -> hiddenServicePublished && notAuthenticated);
    }

    @Override
    public void onTorNodeReady() {
        Log.traceCall();
        p2pServiceListeners.stream().forEach(e -> e.onTorNodeReady());
    }

    @Override
    protected void authenticateToSeedNode() {
        Log.traceCall();
        ((SeedNodePeerManager) peerManager).authenticateToSeedNode();
    }

}
