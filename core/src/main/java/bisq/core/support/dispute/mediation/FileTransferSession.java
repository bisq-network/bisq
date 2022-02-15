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

package bisq.core.support.dispute.mediation;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.FileTransferPart;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.Utilities;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static bisq.network.p2p.network.Connection.getPermittedMessageSize;

@Slf4j
public abstract class FileTransferSession implements MessageListener {
    protected static final int FTP_SESSION_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(60);
    protected static final int FILE_BLOCK_SIZE = getPermittedMessageSize() - 1024;  // allowing space for protobuf

    public interface FtpCallback {
        void onFtpProgress(double progressPct);

        void onFtpComplete(FileTransferSession session);

        void onFtpTimeout(String statusMsg, FileTransferSession session);
    }

    @Getter
    protected final String fullTradeId;
    @Getter
    protected final int traderId;
    @Getter
    protected final String zipId;
    protected final Optional<FtpCallback> ftpCallback;
    protected final NetworkNode networkNode;    // for sending network messages
    protected final NodeAddress peerNodeAddress;
    protected Optional<FileTransferPart> dataAwaitingAck;
    protected long fileOffsetBytes;
    protected long currentBlockSeqNum;
    protected long expectedFileLength;
    protected long lastActivityTime;

    public FileTransferSession(NetworkNode networkNode, NodeAddress peerNodeAddress,
                               String tradeId, int traderId, String traderRole, @Nullable FileTransferSession.FtpCallback callback) {
        this.networkNode = networkNode;
        this.peerNodeAddress = peerNodeAddress;
        this.fullTradeId = tradeId;
        this.traderId = traderId;
        this.ftpCallback = Optional.ofNullable(callback);
        this.zipId = Utilities.getShortId(fullTradeId) + "_" + traderRole.toUpperCase() + "_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        resetSession();
    }

    public void resetSession() {
        lastActivityTime = 0;
        currentBlockSeqNum = -1;
        fileOffsetBytes = 0;
        expectedFileLength = 0;
        dataAwaitingAck = Optional.empty();
        networkNode.removeMessageListener(this);
        log.info("Ftp session parameters have been reset.");
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof FileTransferPart) {
            // mediator receiving log file data
            FileTransferPart ftp = (FileTransferPart) networkEnvelope;
            if (this instanceof FileTransferReceiver) {
                ((FileTransferReceiver) this).processFilePartReceived(ftp);
            }
        } else if (networkEnvelope instanceof AckMessage) {
            AckMessage ackMessage = (AckMessage) networkEnvelope;
            if (ackMessage.getSourceType() == AckMessageSourceType.LOG_TRANSFER) {
                if (ackMessage.isSuccess()) {
                    log.info("Received AckMessage for {} with id {} and uid {}",
                            ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getSourceUid());
                    if (this instanceof FileTransferSender) {
                        ((FileTransferSender) this).processAckForFilePart(ackMessage.getSourceUid());
                    }
                } else {
                    log.warn("Received AckMessage with error state for {} with id {} and errorMessage={}",
                            ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getErrorMessage());
                }
            }
        }
    }

    protected void checkpointLastActivity() {
        lastActivityTime = System.currentTimeMillis();
    }

    protected void initSessionTimer() {
        UserThread.runAfter(() -> {
            if (!transferIsInProgress())    // transfer may have finished before this timer executes
                return;
            if (System.currentTimeMillis() - lastActivityTime < FTP_SESSION_TIMEOUT_MILLIS) {
                log.info("Last activity was {}, we have not yet timed out.", new Date(lastActivityTime));
                initSessionTimer();
            } else {
                log.warn("File transfer session timed out. expected: {} received: {}", expectedFileLength, fileOffsetBytes);
                ftpCallback.ifPresent((e) -> e.onFtpTimeout("Timed out during send", this));
            }
        }, FTP_SESSION_TIMEOUT_MILLIS / 4, TimeUnit.MILLISECONDS);  // check more frequently than the timeout
    }

    protected boolean transferIsInProgress() {
        return fileOffsetBytes != expectedFileLength;
    }

    protected void sendMessage(NetworkEnvelope message, NetworkNode networkNode, NodeAddress nodeAddress) {
        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, message);
        if (future != null) { // is null when testing with Mockito
            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(Connection connection) {
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    String errorSend = "Sending " + message.getClass().getSimpleName() +
                            " to " + nodeAddress.getFullAddress() +
                            " failed. That is expected if the peer is offline.\n\t" +
                            ".\n\tException=" + throwable.getMessage();
                    log.warn(errorSend);
                    ftpCallback.ifPresent((f) -> f.onFtpTimeout("Peer offline", FileTransferSession.this));
                    resetSession();
                }
            }, MoreExecutors.directExecutor());
        }
    }
}
