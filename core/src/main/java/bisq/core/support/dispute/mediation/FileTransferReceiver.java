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
import bisq.network.p2p.network.NetworkNode;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.util.Utilities;

import java.nio.file.FileSystems;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class FileTransferReceiver extends FileTransferSession {
    protected final String zipFilePath;

    public FileTransferReceiver(NetworkNode networkNode, NodeAddress peerNodeAddress,
                                String tradeId, int traderId, String traderRole, @Nullable FileTransferSession.FtpCallback callback) throws IOException {
        super(networkNode, peerNodeAddress, tradeId, traderId, traderRole, callback);
        zipFilePath = ensureReceivingDirectoryExists().getAbsolutePath() + FileSystems.getDefault().getSeparator() + zipId + ".zip";
    }

    public void processFilePartReceived(FileTransferPart ftp) {
        checkpointLastActivity();
        // check that the supplied sequence number is in line with what we are expecting
        if (currentBlockSeqNum < 0) {
            // we have not yet started receiving a file, validate this ftp packet as the initiation request
            initReceiveSession(ftp.uid, ftp.seqNumOrFileLength);
        } else if (currentBlockSeqNum == ftp.seqNumOrFileLength) {
            // we are in the middle of receiving a file; add the block of data to the file
            processReceivedBlock(ftp, networkNode, peerNodeAddress);
        } else {
            log.error("ftp sequence num mismatch, expected {} received {}", currentBlockSeqNum, ftp.seqNumOrFileLength);
            resetSession();    // aborts the file transfer
        }
    }

    public void initReceiveSession(String uid, long expectedFileBytes) {
        networkNode.addMessageListener(this);
        this.expectedFileLength = expectedFileBytes;
        fileOffsetBytes = 0;
        currentBlockSeqNum = 0;
        initSessionTimer();
        log.info("Received a start file transfer request, tradeId={}, traderId={}, size={}", fullTradeId, traderId, expectedFileBytes);
        log.info("New file will be written to {}", zipFilePath);
        UserThread.execute(() -> ackReceivedPart(uid, networkNode, peerNodeAddress));
    }

    private void processReceivedBlock(FileTransferPart ftp, NetworkNode networkNode, NodeAddress peerNodeAddress) {
        try {
            RandomAccessFile file = new RandomAccessFile(zipFilePath, "rwd");
            file.seek(fileOffsetBytes);
            file.write(ftp.messageData.toByteArray(), 0, ftp.messageData.size());
            fileOffsetBytes = fileOffsetBytes + ftp.messageData.size();
            log.info("Sequence number {} for {}, received data {} / {}",
                    ftp.seqNumOrFileLength, Utilities.getShortId(ftp.tradeId), fileOffsetBytes, expectedFileLength);
            currentBlockSeqNum++;
            UserThread.runAfter(() -> {
                ackReceivedPart(ftp.uid, networkNode, peerNodeAddress);
                if (fileOffsetBytes >= expectedFileLength) {
                    log.info("Success!  We have reached the EOF, received {} expected {}", fileOffsetBytes, expectedFileLength);
                    ftpCallback.ifPresent(c -> c.onFtpComplete(this));
                    resetSession();
                }
            }, 100, TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    }

    private void ackReceivedPart(String uid, NetworkNode networkNode, NodeAddress peerNodeAddress) {
        AckMessage ackMessage = new AckMessage(peerNodeAddress,
                AckMessageSourceType.LOG_TRANSFER,
                FileTransferPart.class.getSimpleName(),
                uid,
                Utilities.getShortId(fullTradeId),
                true,           // result
                null);      // errorMessage
        log.info("Send AckMessage for {} to peer {}. id={}, uid={}",
                ackMessage.getSourceMsgClassName(), peerNodeAddress, ackMessage.getSourceId(), ackMessage.getSourceUid());
        sendMessage(ackMessage, networkNode, peerNodeAddress);
    }

    private static File ensureReceivingDirectoryExists() throws IOException {
        File directory = new File(Config.appDataDir() + "/clientLogs");
        if (!directory.exists() && !directory.mkdirs()) {
            log.error("Could not create directory {}", directory.getAbsolutePath());
            throw new IOException("Could not create directory: " + directory.getAbsolutePath());
        }
        return directory;
    }
}
