package io.bisq.core.dao.blockchain.p2p;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.MessageListener;
import io.bisq.network.p2p.network.NetworkNode;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
class RequestBsqBlocksHandler implements MessageListener {
    private static final long TIME_OUT_SEC = 40;
    private NodeAddress peersNodeAddress;
    private Consumer<List<BsqBlock>> blockListHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private Timer timeoutTimer;
    private final int nonce = new Random().nextInt();
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestBsqBlocksHandler(NetworkNode networkNode) {
        this.networkNode = networkNode;
    }

    public void cancel() {
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void request(NodeAddress peersNodeAddress, int fromBlockHeight, Consumer<List<BsqBlock>> blocksHandler) {
        this.blockListHandler = blocksHandler;
        Log.traceCall("nodeAddress=" + peersNodeAddress);
        this.peersNodeAddress = peersNodeAddress;
        if (!stopped) {
            GetBsqBlocksRequest getBsqBlocksRequest = new GetBsqBlocksRequest(fromBlockHeight);

            if (timeoutTimer == null) {
                timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                            if (!stopped) {
                                String errorMessage = "A timeout occurred at sending getBsqBlocksRequest:" + getBsqBlocksRequest +
                                        " on nodeAddress:" + peersNodeAddress;
                                log.error(errorMessage + " / RequestDataHandler=" + RequestBsqBlocksHandler.this);
                                handleFault(errorMessage, peersNodeAddress, CloseConnectionReason.SEND_MSG_TIMEOUT);
                            } else {
                                log.trace("We have stopped already. We ignore that timeoutTimer.run call. " +
                                        "Might be caused by an previous networkNode.sendMessage.onFailure.");
                            }
                        },
                        TIME_OUT_SEC);
            }

            log.error("We send a {} to peer {}. ", getBsqBlocksRequest.getClass().getSimpleName(), peersNodeAddress);
            networkNode.addMessageListener(this);
            SettableFuture<Connection> future = networkNode.sendMessage(peersNodeAddress, getBsqBlocksRequest);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    if (!stopped) {
                        log.trace("Send " + getBsqBlocksRequest + " to " + peersNodeAddress + " succeeded.");
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call." +
                                "Might be caused by an previous timeout.");
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    if (!stopped) {
                        String errorMessage = "Sending getBsqBlocksRequest to " + peersNodeAddress +
                                " failed. That is expected if the peer is offline.\n\t" +
                                "getBsqBlocksRequest=" + getBsqBlocksRequest + "." +
                                "\n\tException=" + throwable.getMessage();
                        log.error(errorMessage);
                        handleFault(errorMessage, peersNodeAddress, CloseConnectionReason.SEND_MSG_FAILURE);
                    } else {
                        log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call. " +
                                "Might be caused by an previous timeout.");
                    }
                }
            });
        } else {
            log.warn("We have stopped already. We ignore that requestData call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        if (connection.getPeersNodeAddressOptional().isPresent() &&
                connection.getPeersNodeAddressOptional().get().equals(peersNodeAddress)) {
            if (networkEnvelop instanceof GetBsqBlocksResponse) {
                Log.traceCall(networkEnvelop.toString() + "\n\tconnection=" + connection);
                if (!stopped) {
                    GetBsqBlocksResponse getBsqBlocksResponse = (GetBsqBlocksResponse) networkEnvelop;
                    stopTimeoutTimer();
                    checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                            "RequestDataHandler.onMessage: connection.getPeersNodeAddressOptional() must be present " +
                                    "at that moment");

                    byte[] payload = getBsqBlocksResponse.getBsqBlocksBytes();
                    List<BsqBlock> list = Utilities.<ArrayList<BsqBlock>>deserialize(payload);
                    log.error("Received {} blocks", list.size());
                    blockListHandler.accept(list);
                    cleanup();
                } else {
                    log.warn("We have stopped already. We ignore that onDataRequest call.");
                }
            }
        } else {
            log.trace("We got a message from another connection and ignore it.");
        }
    }

    public void stop() {
        cleanup();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    @SuppressWarnings("UnusedParameters")
    private void handleFault(String errorMessage, NodeAddress nodeAddress, CloseConnectionReason closeConnectionReason) {
        cleanup();
    }

    private void cleanup() {
        Log.traceCall();
        stopped = true;
        stopTimeoutTimer();
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
