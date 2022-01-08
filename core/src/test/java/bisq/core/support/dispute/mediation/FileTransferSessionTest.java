package bisq.core.support.dispute.mediation;

import bisq.network.p2p.FileTransferPart;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.config.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class FileTransferSessionTest implements FileTransferSession.FtpCallback {

    double notedProgressPct = -1.0;
    int progressInvocations = 0;
    boolean ftpCompleteStatus = false;
    String testTradeId = "foo";
    int testTraderId = 123;
    String testClientId = "bar";
    NetworkNode networkNode;
    NodeAddress counterpartyNodeAddress;

    @Before
    public void setUp() throws Exception {
        new Config();   // static methods like Config.appDataDir() require config to be created once
        networkNode = mock(NetworkNode.class);
        when(networkNode.getNodeAddress()).thenReturn(new NodeAddress("null:0000"));
        counterpartyNodeAddress = new NodeAddress("null:0000");
    }

    @Test
    public void testSendCreate() {
        new FileTransferSender(networkNode, counterpartyNodeAddress, testTradeId, testTraderId, testClientId, this);
        Assert.assertEquals(0.0, notedProgressPct, 0.0);
        Assert.assertEquals(1, progressInvocations);
    }

    @Test
    public void testCreateZip() {
        FileTransferSender sender = new FileTransferSender(networkNode, counterpartyNodeAddress, testTradeId, testTraderId, testClientId, this);
        Assert.assertEquals(0.0, notedProgressPct, 0.0);
        Assert.assertEquals(1, progressInvocations);
        sender.createZipFileToSend();
        File file = new File(sender.zipFilePath);
        Assert.assertTrue(file.exists());
        Assert.assertTrue(file.length() > 0);
    }

    @Test
    public void testSendInitialize() {
        // checks that the initial send request packet contains correct information
        try {
            int testVerifyDataSize = 13;
            FileTransferSender session = initializeSession(testVerifyDataSize);
            session.initSend();
            FileTransferPart ftp = session.dataAwaitingAck.get();
            Assert.assertEquals(ftp.tradeId, testTradeId);
            Assert.assertTrue(ftp.uid.length() > 0);
            Assert.assertEquals(0, ftp.messageData.size());
            Assert.assertEquals(ftp.seqNumOrFileLength, testVerifyDataSize);
            Assert.assertEquals(-1, session.currentBlockSeqNum);
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }
        Assert.fail();
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
            Assert.assertEquals(1, session.currentBlockSeqNum);
            Assert.assertEquals(3, progressInvocations);
            Assert.assertEquals(1.0, notedProgressPct, 0.0);
            Assert.assertTrue(ftpCompleteStatus);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Assert.fail();
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
            Assert.assertEquals(1, session.currentBlockSeqNum);
            Assert.assertEquals(3, progressInvocations);
            Assert.assertEquals(1.0, notedProgressPct, 0.0);
            Assert.assertTrue(ftpCompleteStatus);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Assert.fail();
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
            Assert.assertEquals(2, session.currentBlockSeqNum);
            Assert.assertEquals(4, progressInvocations);
            Assert.assertEquals(1.0, notedProgressPct, 0.0);
            Assert.assertTrue(ftpCompleteStatus);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Assert.fail();
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
            Assert.assertEquals(3, session.currentBlockSeqNum);
            Assert.assertEquals(5, progressInvocations);
            Assert.assertEquals(1.0, notedProgressPct, 0.0);
            Assert.assertTrue(ftpCompleteStatus);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Assert.fail();
        }
    }

    private FileTransferSender initializeSession(int testSize) {
        try {
            FileTransferSender session = new FileTransferSender(networkNode, counterpartyNodeAddress, testTradeId, testTraderId, testClientId, this);
            // simulate a file for sending
            FileWriter fileWriter = new FileWriter(session.zipFilePath);
            char[] buf = new char[testSize];
            for (int x = 0; x < testSize; x++)
                buf[x] = 'A';
            fileWriter.write(buf);
            fileWriter.close();
            Assert.assertFalse(ftpCompleteStatus);
            Assert.assertEquals(1, progressInvocations);
            Assert.assertEquals(0.0, notedProgressPct, 0.0);
            Assert.assertFalse(session.processAckForFilePart("not_expected_uid"));
            return session;
        } catch (IOException e) {
            e.printStackTrace();
        }
        Assert.fail();
        return null;
    }

    private void simulateAckFromPeerAndVerify(FileTransferSender session, int expectedDataSize, long expectedSeqNum, int expectedProgressInvocations) {
        FileTransferPart ftp = session.dataAwaitingAck.get();
        Assert.assertEquals(expectedDataSize, ftp.messageData.size());
        Assert.assertTrue(session.processAckForFilePart(ftp.uid));
        Assert.assertEquals(expectedSeqNum, session.currentBlockSeqNum);
        Assert.assertEquals(expectedProgressInvocations, progressInvocations);
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
