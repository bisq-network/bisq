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

import bisq.common.config.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FileTransferSessionTest implements FileTransferSession.FtpCallback {

    double notedProgressPct = -1.0;
    int progressInvocations = 0;
    boolean ftpCompleteStatus = false;
    String testTradeId = "foo";
    int testTraderId = 123;
    String testClientId = "bar";
    NetworkNode networkNode;
    NodeAddress counterpartyNodeAddress;

    @BeforeEach
    public void setUp() throws Exception {
        new Config();   // static methods like Config.appDataDir() require config to be created once
        networkNode = mock(NetworkNode.class);
        when(networkNode.getNodeAddress()).thenReturn(new NodeAddress("null:0000"));
        counterpartyNodeAddress = new NodeAddress("null:0000");
    }

    @Test
    public void testSendCreate() {
        new FileTransferSender(networkNode, counterpartyNodeAddress, testTradeId, testTraderId, testClientId, true, this);
        assertEquals(0.0, notedProgressPct, 0.0);
        assertEquals(1, progressInvocations);
    }

    @Test
    public void testCreateZip() {
        FileTransferSender sender = new FileTransferSender(networkNode, counterpartyNodeAddress, testTradeId, testTraderId, testClientId, true, this);
        assertEquals(0.0, notedProgressPct, 0.0);
        assertEquals(1, progressInvocations);
        sender.createZipFileToSend();
        File file = new File(sender.zipFilePath);
        assertTrue(file.getAbsoluteFile().exists());
        assertTrue(file.getAbsoluteFile().length() > 0);
        file.deleteOnExit();
    }

    @Test
    public void testSendInitialize() {
        // checks that the initial send request packet contains correct information
        try {
            int testVerifyDataSize = 13;
            FileTransferSender session = initializeSession(testVerifyDataSize);
            session.initSend();
            FileTransferPart ftp = session.dataAwaitingAck.get();
            assertEquals(ftp.tradeId, testTradeId);
            assertTrue(ftp.uid.length() > 0);
            assertEquals(0, ftp.messageData.size());
            assertEquals(ftp.seqNumOrFileLength, testVerifyDataSize);
            assertEquals(-1, session.currentBlockSeqNum);
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }
        fail();
    }

    @Test
    public void testSendSmallFile() {
        try {
            int testVerifyDataSize = 13;
            FileTransferSender session = initializeSession(testVerifyDataSize);
            // the first block contains zero data, as it is a "request to send"
            session.initSend();
            simulateAckFromPeerAndVerify(session, 0, 0, 2);
            // the second block contains all the test file data (because it is a small file)
            session.sendNextBlock();
            simulateAckFromPeerAndVerify(session, testVerifyDataSize, 1, 3);
            // the final invocation sends no data, and wraps up the session
            session.sendNextBlock();
            assertEquals(1, session.currentBlockSeqNum);
            assertEquals(3, progressInvocations);
            assertEquals(1.0, notedProgressPct, 0.0);
            assertTrue(ftpCompleteStatus);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail();
        }
    }

    @Test
    public void testSendOneFullBlock() {
        try {
            int testVerifyDataSize = FileTransferSession.FILE_BLOCK_SIZE;
            FileTransferSender session = initializeSession(testVerifyDataSize);
            // the first block contains zero data, as it is a "request to send"
            session.initSend();
            simulateAckFromPeerAndVerify(session, 0, 0, 2);
            // the second block contains all the test file data (because it is a small file)
            session.sendNextBlock();
            simulateAckFromPeerAndVerify(session, testVerifyDataSize, 1, 3);
            // the final invocation sends no data, and wraps up the session
            session.sendNextBlock();
            assertEquals(1, session.currentBlockSeqNum);
            assertEquals(3, progressInvocations);
            assertEquals(1.0, notedProgressPct, 0.0);
            assertTrue(ftpCompleteStatus);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail();
        }
    }

    @Test
    public void testSendTwoFullBlocks() {
        try {
            int testVerifyDataSize = FileTransferSession.FILE_BLOCK_SIZE * 2;
            FileTransferSender session = initializeSession(testVerifyDataSize);
            // the first block contains zero data, as it is a "request to send"
            session.initSend();
            simulateAckFromPeerAndVerify(session, 0, 0, 2);
            // the second block contains half of the test file data
            session.sendNextBlock();
            simulateAckFromPeerAndVerify(session, testVerifyDataSize / 2, 1, 3);
            // the third block contains half of the test file data
            session.sendNextBlock();
            simulateAckFromPeerAndVerify(session, testVerifyDataSize / 2, 2, 4);
            // the final invocation sends no data, and wraps up the session
            session.sendNextBlock();
            assertEquals(2, session.currentBlockSeqNum);
            assertEquals(4, progressInvocations);
            assertEquals(1.0, notedProgressPct, 0.0);
            assertTrue(ftpCompleteStatus);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail();
        }
    }

    @Test
    public void testSendTwoFullBlocksPlusOneByte() {
        try {
            int testVerifyDataSize = 1 + FileTransferSession.FILE_BLOCK_SIZE * 2;
            FileTransferSender session = initializeSession(testVerifyDataSize);
            // the first block contains zero data, as it is a "request to send"
            session.initSend();
            simulateAckFromPeerAndVerify(session, 0, 0, 2);
            session.sendNextBlock();
            simulateAckFromPeerAndVerify(session, FileTransferSession.FILE_BLOCK_SIZE, 1, 3);
            session.sendNextBlock();
            simulateAckFromPeerAndVerify(session, FileTransferSession.FILE_BLOCK_SIZE, 2, 4);
            // the fourth block contains one byte
            session.sendNextBlock();
            simulateAckFromPeerAndVerify(session, 1, 3, 5);
            // the final invocation sends no data, and wraps up the session
            session.sendNextBlock();
            assertEquals(3, session.currentBlockSeqNum);
            assertEquals(5, progressInvocations);
            assertEquals(1.0, notedProgressPct, 0.0);
            assertTrue(ftpCompleteStatus);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail();
        }
    }

    private FileTransferSender initializeSession(int testSize) {
        try {
            FileTransferSender session = new FileTransferSender(networkNode, counterpartyNodeAddress, testTradeId, testTraderId, testClientId, true, this);
            // simulate a file for sending
            FileWriter fileWriter = new FileWriter(session.zipFilePath);
            char[] buf = new char[testSize];
            for (int x = 0; x < testSize; x++)
                buf[x] = 'A';
            fileWriter.write(buf);
            fileWriter.close();
            assertFalse(ftpCompleteStatus);
            assertEquals(1, progressInvocations);
            assertEquals(0.0, notedProgressPct, 0.0);
            assertFalse(session.processAckForFilePart("not_expected_uid"));
            return session;
        } catch (IOException e) {
            e.printStackTrace();
        }
        fail();
        return null;
    }

    private void simulateAckFromPeerAndVerify(FileTransferSender session, int expectedDataSize, long expectedSeqNum, int expectedProgressInvocations) {
        FileTransferPart ftp = session.dataAwaitingAck.get();
        assertEquals(expectedDataSize, ftp.messageData.size());
        assertTrue(session.processAckForFilePart(ftp.uid));
        assertEquals(expectedSeqNum, session.currentBlockSeqNum);
        assertEquals(expectedProgressInvocations, progressInvocations);
    }

    @Override
    public void onFtpProgress(double progressPct) {
        notedProgressPct = progressPct;
        progressInvocations++;
    }

    @Override
    public void onFtpComplete(FileTransferSession session) {
        ftpCompleteStatus = true;
    }

    @Override
    public void onFtpTimeout(String status, FileTransferSession session) {
    }
}
