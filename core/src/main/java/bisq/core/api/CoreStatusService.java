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
class CoreStatusService {

    private final BsqNode bsqNode;
    private final DaoStateService daoStateService;
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final Balances balances;

    @Inject
    public CoreStatusService(BsqNodeProvider bsqNodeProvider,
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

    public boolean getStatus() {
        // Informs the client and other API methods that the server's p2p network is ready
        // and bootstrapped, the block chain is complete, at least 4 peers are connected,
        // and the wallet is available.  If all checks pass, true is returned, else false.
        boolean isReady = true;
        if (!bsqNode.isP2pNetworkReady()) {
            log.warn("p2p network is not yet ready");
            isReady = false;
        }

        if (!p2PService.isBootstrapped()) {
            log.warn("p2p service is not yet bootstrapped");
            isReady = false;
        }

        if (!daoStateService.isParseBlockChainComplete()) {
            log.warn("dao block chain is not yet complete");
            isReady = false;
        }

        if (p2PService.getNumConnectedPeers().get() < 4) {
            log.warn("not enough connected peers");
            isReady = false;
        }

        if (!walletsManager.areWalletsAvailable()) {
            log.warn("wallet is not yet available");
            isReady = false;
        }

        if (balances.getAvailableBalance().get() == null) {
            log.warn("balance is not yet available");
            isReady = false;
        }

        return isReady;
    }
}
