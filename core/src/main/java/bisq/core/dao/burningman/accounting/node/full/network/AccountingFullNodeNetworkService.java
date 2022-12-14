/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.burningman.accounting.node.full.network;

import bisq.core.dao.burningman.accounting.BurningManAccountingService;
import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.node.AccountingNode;
import bisq.core.dao.burningman.accounting.node.messages.GetAccountingBlocksRequest;
import bisq.core.dao.burningman.accounting.node.messages.NewAccountingBlockBroadcastMessage;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.proto.network.NetworkEnvelope;

import org.bitcoinj.core.ECKey;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.math.BigInteger;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static org.bitcoinj.core.Utils.HEX;

// Taken from FullNodeNetworkService
@Singleton
@Slf4j
public class AccountingFullNodeNetworkService implements MessageListener, PeerManager.Listener {
    private static final long CLEANUP_TIMER = 120;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Broadcaster broadcaster;
    private final BurningManAccountingService burningManAccountingService;
    private final boolean useDevPrivilegeKeys;
    @Nullable
    private final String bmOracleNodePubKey;
    @Nullable
    private final ECKey bmOracleNodePrivKey;

    // Key is connection UID
    private final Map<String, GetAccountingBlocksRequestHandler> getBlocksRequestHandlers = new HashMap<>();
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountingFullNodeNetworkService(NetworkNode networkNode,
                                            PeerManager peerManager,
                                            Broadcaster broadcaster,
                                            BurningManAccountingService burningManAccountingService,
                                            @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys,
                                            @Named(Config.BM_ORACLE_NODE_PUB_KEY) String bmOracleNodePubKey,
                                            @Named(Config.BM_ORACLE_NODE_PRIV_KEY) String bmOracleNodePrivKey) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.broadcaster = broadcaster;
        this.burningManAccountingService = burningManAccountingService;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;

        if (useDevPrivilegeKeys) {
            bmOracleNodePubKey = DevEnv.DEV_PRIVILEGE_PUB_KEY;
            bmOracleNodePrivKey = DevEnv.DEV_PRIVILEGE_PRIV_KEY;
        }
        this.bmOracleNodePubKey = bmOracleNodePubKey.isEmpty() ? null : bmOracleNodePubKey;
        this.bmOracleNodePrivKey = bmOracleNodePrivKey.isEmpty() ? null : toEcKey(bmOracleNodePrivKey);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListeners() {
        networkNode.addMessageListener(this);
        peerManager.addListener(this);
    }

    public void shutDown() {
        stopped = true;
        networkNode.removeMessageListener(this);
        peerManager.removeListener(this);
    }


    public void publishAccountingBlock(AccountingBlock block) {
        if (bmOracleNodePubKey == null || bmOracleNodePrivKey == null) {
            log.warn("Ignore publishNewBlock call. bmOracleNodePubKey or bmOracleNodePrivKey are not set up");
            return;
        }

        checkArgument(AccountingNode.isPermittedPubKey(useDevPrivilegeKeys, bmOracleNodePubKey),
                "The bmOracleNodePubKey must be included in the hard coded list of supported pub keys");

        log.info("Publish new block at height={}", block.getHeight());
        byte[] signature = AccountingNode.getSignature(AccountingNode.getSha256Hash(block), bmOracleNodePrivKey);
        NewAccountingBlockBroadcastMessage message = new NewAccountingBlockBroadcastMessage(block, bmOracleNodePubKey, signature);
        broadcaster.broadcast(message, networkNode.getNodeAddress());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        stopped = true;
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        stopped = false;
    }

    @Override
    public void onAwakeFromStandby() {
        stopped = false;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetAccountingBlocksRequest) {
            handleGetBlocksRequest((GetAccountingBlocksRequest) networkEnvelope, connection);
        }
    }

    private void handleGetBlocksRequest(GetAccountingBlocksRequest getBlocksRequest, Connection connection) {
        if (bmOracleNodePubKey == null || bmOracleNodePrivKey == null) {
            log.warn("Ignore handleGetBlocksRequest call. bmOracleNodePubKey or bmOracleNodePrivKey are not set up");
            return;
        }

        checkArgument(AccountingNode.isPermittedPubKey(useDevPrivilegeKeys, bmOracleNodePubKey),
                "The bmOracleNodePubKey must be included in the hard coded list of supported pub keys");

        if (stopped) {
            log.warn("We have stopped already. We ignore that onMessage call.");
            return;
        }

        String uid = connection.getUid();
        if (getBlocksRequestHandlers.containsKey(uid)) {
            log.warn("We have already a GetDataRequestHandler for that connection started. " +
                    "We start a cleanup timer if the handler has not closed by itself in between 2 minutes.");

            UserThread.runAfter(() -> {
                if (getBlocksRequestHandlers.containsKey(uid)) {
                    GetAccountingBlocksRequestHandler handler = getBlocksRequestHandlers.get(uid);
                    handler.stop();
                    getBlocksRequestHandlers.remove(uid);
                }
            }, CLEANUP_TIMER);
            return;
        }

        GetAccountingBlocksRequestHandler requestHandler = new GetAccountingBlocksRequestHandler(networkNode,
                burningManAccountingService,
                bmOracleNodePrivKey,
                bmOracleNodePubKey,
                new GetAccountingBlocksRequestHandler.Listener() {
                    @Override
                    public void onComplete() {
                        getBlocksRequestHandlers.remove(uid);
                    }

                    @Override
                    public void onFault(String errorMessage, @Nullable Connection connection) {
                        getBlocksRequestHandlers.remove(uid);
                        if (!stopped) {
                            log.trace("GetDataRequestHandler failed.\n\tConnection={}\n\t" +
                                    "ErrorMessage={}", connection, errorMessage);
                            if (connection != null) {
                                peerManager.handleConnectionFault(connection);
                            }
                        } else {
                            log.warn("We have stopped already. We ignore that getDataRequestHandler.handle.onFault call.");
                        }
                    }
                });
        getBlocksRequestHandlers.put(uid, requestHandler);
        requestHandler.onGetBlocksRequest(getBlocksRequest, connection);
    }

    private ECKey toEcKey(String bmOracleNodePrivKey) {
        try {
            return ECKey.fromPrivate(new BigInteger(1, HEX.decode(bmOracleNodePrivKey)));
        } catch (Throwable t) {
            log.error("Error at creating EC key out of bmOracleNodePrivKey", t);
            return null;
        }
    }
}
