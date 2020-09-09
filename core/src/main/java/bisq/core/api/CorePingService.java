package bisq.core.api;

import bisq.core.btc.Balances;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.state.DaoStateService;

import bisq.network.p2p.P2PService;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CorePingService {

    private final BsqNode bsqNode;
    private final DaoStateService daoStateService;
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final Balances balances;

    @Inject
    public CorePingService(BsqNodeProvider bsqNodeProvider,
                           DaoStateService daoStateService,
                           P2PService p2PService,
                           WalletsManager walletsManager,
                           Balances balances) {
        this.bsqNode = bsqNodeProvider.getBsqNode();
        this.p2PService = p2PService;
        this.daoStateService = daoStateService;
        this.walletsManager = walletsManager;
        this.balances = balances;
    }

    @SuppressWarnings("SameReturnValue")
    public int ping() {
        // A successful ping informs the client and other API methods that the server is
        // ready to perform complex functions beyond accepting gRPC calls.  If the checks
        // below pass, return a pong (1), else an exception with a message explaining the
        // failure.
        if (!bsqNode.isP2pNetworkReady())
            throw new IllegalStateException("p2p network is not yet ready");

        if (!p2PService.isBootstrapped())
            throw new IllegalStateException("p2p service is not yet bootstrapped");

        if (!daoStateService.isParseBlockChainComplete())
            throw new IllegalStateException("dao block chain is not yet complete");

        if (p2PService.getNumConnectedPeers().get() < 4)
            throw new IllegalStateException("not enough connected peers");

        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");

        if (balances.getAvailableBalance().get() == null)
            throw new IllegalStateException("balance is not yet available");

        return 1;
    }
}
