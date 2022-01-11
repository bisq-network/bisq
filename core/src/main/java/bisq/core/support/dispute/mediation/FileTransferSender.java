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

import bisq.network.p2p.FileTransferPart;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.UserThread;
import bisq.common.config.Config;

import com.google.protobuf.ByteString;

import java.net.URI;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.io.IOException;
import java.io.RandomAccessFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.common.file.FileUtil.doesFileContainKeyword;

@Slf4j
public class FileTransferSender extends FileTransferSession {
    protected final String zipFilePath;

    public FileTransferSender(NetworkNode networkNode, NodeAddress peerNodeAddress,
                              String tradeId, int traderId, String traderRole, @Nullable FileTransferSession.FtpCallback callback) {
        super(networkNode, peerNodeAddress, tradeId, traderId, traderRole, callback);
        zipFilePath = Config.appDataDir() + FileSystems.getDefault().getSeparator() + zipId + ".zip";
        updateProgress();
    }

    public void createZipFileToSend() {
        try {
            Map<String, String> env = new HashMap<>();
            env.put("create", "true");
            URI uri = URI.create("jar:file:///" + zipFilePath
                    .replace('\\', '/')
                    .replaceAll(" ", "%20"));
            FileSystem zipfs = FileSystems.newFileSystem(uri, env);
            Files.createDirectory(zipfs.getPath(zipId));    // store logfiles in a usefully-named subdir
            Stream<Path> paths = Files.walk(Paths.get(Config.appDataDir().toString()), 1);
            paths.filter(Files::isRegularFile).forEach(externalTxtFile -> {
                try {
                    // always include bisq.log; and other .log files if they contain the TradeId
                    if (externalTxtFile.getFileName().toString().equals("bisq.log") ||
                            (externalTxtFile.getFileName().toString().matches(".*.log") &&
                                    doesFileContainKeyword(externalTxtFile.toFile(), fullTradeId))) {
                        Path pathInZipfile = zipfs.getPath(zipId + "/" + externalTxtFile.getFileName().toString());
                        log.info("adding {} to zip file {}", pathInZipfile, zipfs);
                        Files.copy(externalTxtFile, pathInZipfile, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                }
            });
            zipfs.close();
        } catch (IOException | IllegalArgumentException ex) {
            log.error(ex.toString());
            ex.printStackTrace();
        }
    }

    public void initSend() throws IOException {
        initSessionTimer();
        networkNode.addMessageListener(this);
        RandomAccessFile file = new RandomAccessFile(zipFilePath, "r");
        expectedFileLength = file.length();
        file.close();
        // an empty block is sent as request to initiate file transfer, peer must ACK for transfer to continue
        dataAwaitingAck = Optional.of(new FileTransferPart(networkNode.getNodeAddress(), fullTradeId, traderId, UUID.randomUUID().toString(), expectedFileLength, ByteString.EMPTY));
        uploadData();
    }

    public void sendNextBlock() throws IOException, IllegalStateException {
        if (dataAwaitingAck.isPresent()) {
            log.warn("prepNextBlockToSend invoked, but we are still waiting for a previous ACK");
            throw new IllegalStateException("prepNextBlockToSend invoked, but we are still waiting for a previous ACK");
        }
        RandomAccessFile file = new RandomAccessFile(zipFilePath, "r");
        file.seek(fileOffsetBytes);
        byte[] buff = new byte[FILE_BLOCK_SIZE];
        int nBytesRead = file.read(buff, 0, FILE_BLOCK_SIZE);
        file.close();
        if (nBytesRead < 0) {
            log.info("Success!  We have reached the EOF, {} bytes sent.  Removing zip file {}", fileOffsetBytes, zipFilePath);
            Files.delete(Paths.get(zipFilePath));
            ftpCallback.ifPresent(c -> c.onFtpComplete(this));
            UserThread.runAfter(this::resetSession, 1);
            return;
        }
        dataAwaitingAck = Optional.of(new FileTransferPart(networkNode.getNodeAddress(), fullTradeId, traderId, UUID.randomUUID().toString(), currentBlockSeqNum, ByteString.copyFrom(buff, 0, nBytesRead)));
        uploadData();
    }

    public void retrySend() {
        if (transferIsInProgress()) {
            log.info("Retry send of current block");
            initSessionTimer();
            uploadData();
        } else {
            UserThread.runAfter(() -> ftpCallback.ifPresent((f) -> f.onFtpTimeout("Could not re-send", this)), 1);
        }
    }

    protected void uploadData() {
        if (dataAwaitingAck.isEmpty()) {
            return;
        }
        FileTransferPart ftp = dataAwaitingAck.get();
        log.info("Send FileTransferPart seq {} length {} to peer {}, UID={}",
                ftp.seqNumOrFileLength, ftp.messageData.size(), peerNodeAddress, ftp.uid);
        sendMessage(ftp, networkNode, peerNodeAddress);
    }

    public boolean processAckForFilePart(String ackUid) {
        if (dataAwaitingAck.isEmpty()) {
            log.warn("We received an ACK we were not expecting. {}", ackUid);
            return false;
        }
        if (!dataAwaitingAck.get().uid.equals(ackUid)) {
            log.warn("We received an ACK that has a different UID to what we were expecting.  We ignore and wait for the correct ACK");
            log.info("Received {} expecting {}", ackUid, dataAwaitingAck.get().uid);
            return false;
        }
        // fileOffsetBytes gets incremented by the size of the block that was ack'd
        fileOffsetBytes += dataAwaitingAck.get().messageData.size();
        currentBlockSeqNum++;
        dataAwaitingAck = Optional.empty();
        checkpointLastActivity();
        updateProgress();
        UserThread.runAfter(() -> {        // to trigger continuing the file transfer
            try {
                sendNextBlock();
            } catch (IOException e) {
                log.error(e.toString());
                e.printStackTrace();
            }
        }, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    public void updateProgress() {
        double progressPct = expectedFileLength > 0 ?
                ((double) fileOffsetBytes / expectedFileLength) : 0.0;
        ftpCallback.ifPresent(c -> c.onFtpProgress(progressPct));
        log.info("ftp progress: {}", String.format("%.0f%%", progressPct * 100));
    }
}
