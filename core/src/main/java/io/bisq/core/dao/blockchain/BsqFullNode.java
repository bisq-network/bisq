/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.blockchain;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.network.Msg;
import io.bisq.common.proto.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.json.JsonExporter;
import io.bisq.core.dao.blockchain.p2p.GetBsqBlocksRequest;
import io.bisq.core.dao.blockchain.p2p.GetBsqBlocksResponse;
import io.bisq.core.dao.blockchain.p2p.NewBsqBlockBroadcastMsg;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.network.Connection;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// We are in UserThread context. We get callbacks from threaded classes which are already mapped to the UserThread.
@Slf4j
public class BsqFullNode extends BsqNode {

    private final BsqBlockchainRequest bsqBlockchainRequest;
    private final JsonExporter jsonExporter;
    @Getter
    private boolean parseBlockchainComplete;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqFullNode(BisqEnvironment bisqEnvironment,
                       P2PService p2PService,
                       BsqChainState bsqChainState,
                       BsqBlockchainRequest bsqBlockchainRequest,
                       BsqParser bsqParser,
                       JsonExporter jsonExporter,
                       PersistenceProtoResolver persistenceProtoResolver,
                       @Named(Storage.STORAGE_DIR) File storageDir) {
        super(bisqEnvironment,
                p2PService,
                bsqChainState,
                bsqParser,
                persistenceProtoResolver,
                storageDir);
        this.bsqBlockchainRequest = bsqBlockchainRequest;
        this.jsonExporter = jsonExporter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        super.onAllServicesInitialized(errorMessageHandler);

        bsqBlockchainRequest.setup(this::startParseBlocks,
                throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                });
    }

    @Override
    protected void parseBlocksWithChainHeadHeight(int startBlockHeight, int genesisBlockHeight, String genesisTxId) {
        bsqBlockchainRequest.requestChainHeadHeight(chainHeadHeight -> {
            parseBlocks(startBlockHeight, genesisBlockHeight, genesisTxId, chainHeadHeight);
        }, throwable -> {
            log.error(throwable.toString());
            throwable.printStackTrace();
        });
    }

    @Override
    protected void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId, Integer chainHeadHeight) {
        if (chainHeadHeight != startBlockHeight) {
            bsqBlockchainRequest.parseBlocks(startBlockHeight,
                    chainHeadHeight,
                    genesisBlockHeight,
                    genesisTxId,
                    this::onNewBsqBlock,
                    () -> {
                        // we are done but it might be that new blocks have arrived in the meantime,
                        // so we try again with startBlockHeight set to current chainHeadHeight
                        // We also set up the listener in the else main branch where we check  
                        // if we at chainTip, so do nto include here another check as it would
                        // not trigger the listener registration.
                        parseBlocksWithChainHeadHeight(chainHeadHeight,
                                genesisBlockHeight,
                                genesisTxId);
                    }, throwable -> {
                        if (throwable instanceof BlockNotConnectingException) {
                            startReOrgFromLastSnapshot(((BlockNotConnectingException) throwable).getBlock());
                        } else {
                            log.error(throwable.toString());
                            throwable.printStackTrace();
                        }
                    });
        } else {
            // We dont have received new blocks in the meantime so we are completed and we register our handler
            onParseBlockchainComplete(genesisBlockHeight, genesisTxId);
        }
    }

    @Override
    protected void onParseBlockchainComplete(int genesisBlockHeight, String genesisTxId) {
        parseBlockchainComplete = true;
        // We register our handler for new blocks
        bsqBlockchainRequest.addBlockHandler(btcdBlock -> {
            bsqBlockchainRequest.parseBlock(btcdBlock,
                    genesisBlockHeight,
                    genesisTxId,
                    this::onNewBsqBlock,
                    throwable -> {
                        if (throwable instanceof BlockNotConnectingException) {
                            startReOrgFromLastSnapshot(((BlockNotConnectingException) throwable).getBlock());
                        } else {
                            log.error(throwable.toString());
                            throwable.printStackTrace();
                        }
                    });
        });

        p2PService.getNetworkNode().addMessageListener(this::onMessage);
    }

    private void onMessage(Msg msg, Connection connection) {
        if (msg instanceof GetBsqBlocksRequest && connection.getPeersNodeAddressOptional().isPresent()) {
            GetBsqBlocksRequest getBsqBlocksRequest = (GetBsqBlocksRequest) msg;
            final NodeAddress peersNodeAddress = connection.getPeersNodeAddressOptional().get();
            log.debug("Received getBsqBlocksRequest with data: {} from {}",
                    getBsqBlocksRequest.getFromBlockHeight(), peersNodeAddress);

            final List<BsqBlock> bsqBlockList = bsqChainState.getBlocksFrom(getBsqBlocksRequest.getFromBlockHeight());
            byte[] bsqBlockListBytes = Utilities.<ArrayList<BsqBlock>>serialize(new ArrayList<>(bsqBlockList));
            final GetBsqBlocksResponse bsqBlocksResponse = new GetBsqBlocksResponse(bsqBlockListBytes);
            SettableFuture<Connection> future = p2PService.getNetworkNode().sendMessage(peersNodeAddress,
                    bsqBlocksResponse);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    log.trace("onSuccess Send " + bsqBlocksResponse + " to " + peersNodeAddress + " succeeded.");
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.error(throwable.toString());
                }
            });
        }
    }

    @Override
    protected void onNewBsqBlock(BsqBlock bsqBlock) {
        super.onNewBsqBlock(bsqBlock);
        jsonExporter.maybeExport();
        if (parseBlockchainComplete && p2pNetworkReady)
            publishNewBlock(bsqBlock);
    }

    private void publishNewBlock(BsqBlock bsqBlock) {
        byte[] bsqBlockBytes = Utilities.<BsqBlock>serialize(bsqBlock);
        final NewBsqBlockBroadcastMsg newBsqBlockBroadcastMsg = new NewBsqBlockBroadcastMsg(bsqBlockBytes);
        p2PService.getBroadcaster().broadcast(newBsqBlockBroadcastMsg, p2PService.getNetworkNode().getNodeAddress(), null, true);
    }
}
