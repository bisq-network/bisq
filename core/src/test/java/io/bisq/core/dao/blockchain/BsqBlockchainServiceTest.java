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

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.domain.*;
import org.bitcoinj.core.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.assertEquals;

/*
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
 */

public class BsqBlockchainServiceTest {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainServiceTest.class);
    private MockBsqBlockchainService squBlockchainService;

    @Before
    public void setup() {
        squBlockchainService = new MockBsqBlockchainService();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGenesisBlock() throws BsqBlockchainException, BitcoindException, CommunicationException {
        int genesisBlockHeight = 0;
        String genesisTxId = "000000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        buildGenesisBlock(genesisBlockHeight, genesisTxId);
        Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap = squBlockchainService.parseBlockchain(new HashMap<>(),
                squBlockchainService.requestChainHeadHeight(),
                genesisBlockHeight,
                genesisTxId);
        BsqUTXO bsqUTXO1 = utxoByTxIdMap.get(genesisTxId).get(0);
        BsqUTXO bsqUTXO2 = utxoByTxIdMap.get(genesisTxId).get(1);
        assertEquals(1, utxoByTxIdMap.size());
        assertEquals("addressGen1", bsqUTXO1.getAddress());
        assertEquals("addressGen2", bsqUTXO2.getAddress());
    }

    @Test
    public void testGenToTx1Block1() throws BsqBlockchainException, BitcoindException, CommunicationException {
        int genesisBlockHeight = 0;
        String genesisTxId = "000000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        buildGenesisBlock(genesisBlockHeight, genesisTxId);

        // We spend from output 1 of gen tx
        String txId = "100000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        buildSpendingTx(genesisBlockHeight,
                genesisTxId,
                txId,
                1,
                0,
                0.00001000,
                "addressTx1");

        Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap = squBlockchainService.parseBlockchain(new HashMap<>(),
                squBlockchainService.requestChainHeadHeight(),
                genesisBlockHeight,
                genesisTxId);

        BsqUTXO bsqUTXO1 = utxoByTxIdMap.get(genesisTxId).get(0);
        BsqUTXO bsqUTXO2 = utxoByTxIdMap.get(txId).get(0);
        assertEquals(2, utxoByTxIdMap.size());
        assertEquals("addressGen1", bsqUTXO1.getAddress());
        assertEquals("addressTx1", bsqUTXO2.getAddress());
    }

    @Test
    public void testGenToTx1toTx2Block1() throws BsqBlockchainException, BitcoindException, CommunicationException {
        int genesisBlockHeight = 0;
        String genesisTxId = "000000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        buildGenesisBlock(genesisBlockHeight, genesisTxId);

        // We spend from output 1 of gen tx
        String tx1Id = "100000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        buildSpendingTx(genesisBlockHeight,
                genesisTxId,
                tx1Id,
                1,
                0,
                0.00001000,
                "addressTx1");

        // We spend from output 0 of tx1 (same block)
        String tx2Id = "200000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        buildSpendingTx(1,
                tx1Id,
                tx2Id,
                0,
                0,
                0.00001000,
                "addressTx2");

        Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap = squBlockchainService.parseBlockchain(new HashMap<>(),
                squBlockchainService.requestChainHeadHeight(),
                genesisBlockHeight,
                genesisTxId);

        BsqUTXO bsqUTXO1 = utxoByTxIdMap.get(genesisTxId).get(0);
        BsqUTXO bsqUTXO2 = utxoByTxIdMap.get(tx2Id).get(0);
        assertEquals(2, utxoByTxIdMap.size());
        assertEquals("addressGen1", bsqUTXO1.getAddress());
        assertEquals("addressTx2", bsqUTXO2.getAddress());
    }

    @Test
    public void testGenToTx1toTx2AndGenToTx2Block1() throws BsqBlockchainException, BitcoindException, CommunicationException {
        int genesisBlockHeight = 0;
        String genesisTxId = "000000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        buildGenesisBlock(genesisBlockHeight, genesisTxId);

        // We spend from output 1 of gen tx
        String tx1Id = "100000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        buildSpendingTx(genesisBlockHeight,
                genesisTxId,
                tx1Id,
                1,
                0,
                0.00001000,
                "addressTx1");

        // We spend from output 0 of tx1 (same block)
        String tx2Id = "200000a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627";
        RawTransaction tx2 = buildSpendingTx(1,
                tx1Id,
                tx2Id,
                0,
                0,
                0.00001000,
                "addressTx3a");

        // We spend from output 0 of gen tx to tx2
        List<RawInput> rawInputs = tx2.getVIn();
        rawInputs.add(getRawInput(0, genesisTxId));
        tx2.setVIn(rawInputs);

        List<RawOutput> rawOutputs = tx2.getVOut();
        rawOutputs.add(getRawOutput(0, 0.00005000, "addressTx3b"));
        tx2.setVOut(rawOutputs);


        Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap = squBlockchainService.parseBlockchain(new HashMap<>(),
                squBlockchainService.requestChainHeadHeight(),
                genesisBlockHeight,
                genesisTxId);

        BsqUTXO bsqUTXO1 = utxoByTxIdMap.get(tx2Id).get(0);
        BsqUTXO bsqUTXO2 = utxoByTxIdMap.get(tx2Id).get(1);
        assertEquals(1, utxoByTxIdMap.size());
        assertEquals("addressTx3a", bsqUTXO1.getAddress());
        assertEquals("addressTx3b", bsqUTXO2.getAddress());
    }


    private RawTransaction buildSpendingTx(int inputTxBlockHeight,
                                           String inputTxId,
                                           String txId,
                                           int inputIndex,
                                           int outputIndex,
                                           double outputValue,
                                           String outputAddress) {
        RawTransaction rawTransaction = getRawTransaction(inputTxBlockHeight + 1, txId);

        List<RawInput> rawInputs = new ArrayList<>();
        rawInputs.add(getRawInput(inputIndex, inputTxId));
        rawTransaction.setVIn(rawInputs);

        List<RawOutput> rawOutputs = new ArrayList<>();
        rawOutputs.add(getRawOutput(outputIndex, outputValue, outputAddress));
        rawTransaction.setVOut(rawOutputs);

        squBlockchainService.addTxToBlock(1, rawTransaction);
        squBlockchainService.buildBlocks(0, 1);
        return rawTransaction;
    }

    private void buildGenesisBlock(int genesisBlockHeight, String genesisTxId) throws BsqBlockchainException, BitcoindException, CommunicationException {
        RawTransaction genesisRawTransaction = getRawTransaction(genesisBlockHeight, genesisTxId);

        List<RawInput> rawInputs = new ArrayList<>();
        rawInputs.add(getRawInput(0, "000001a4d94cb612b5d722d531083f59f317d5dea1db4a191f61b2ab34af2627"));
        genesisRawTransaction.setVIn(rawInputs);

        List<RawOutput> rawOutputs = new ArrayList<>();
        rawOutputs.add(getRawOutput(0, 0.00005000, "addressGen1"));
        rawOutputs.add(getRawOutput(1, 0.00001000, "addressGen2"));
        genesisRawTransaction.setVOut(rawOutputs);

        squBlockchainService.setGenesisTx(genesisTxId, genesisBlockHeight);
        squBlockchainService.addTxToBlock(0, genesisRawTransaction);
        squBlockchainService.buildBlocks(0, 0);
    }

    private RawTransaction getRawTransaction(int genesisBlockHeight, String txId) {
        RawTransaction genesisRawTransaction = new RawTransaction();
        genesisRawTransaction.setBlockHash("BlockHash" + genesisBlockHeight);
        genesisRawTransaction.setTxId(txId);
        return genesisRawTransaction;
    }

    private RawOutput getRawOutput(int index, double value, String address) {
        RawOutput rawOutput = new RawOutput();
        rawOutput.setN(index);
        rawOutput.setValue(BigDecimal.valueOf((long) (value * 100000000), 8));
        PubKeyScript scriptPubKey = new PubKeyScript();
        scriptPubKey.setAddresses(Arrays.asList(address));
        rawOutput.setScriptPubKey(scriptPubKey);
        return rawOutput;
    }

    private RawInput getRawInput(int index, String spendingTxId) {
        RawInput rawInput = new RawInput();
        rawInput.setTxId(spendingTxId);
        rawInput.setVOut(index);
        return rawInput;
    }

    private String getHex(String txId) {
        byte[] bytes = new byte[32];
        byte[] inputBytes = txId.getBytes();
        for (int i = 0; i < 32; i++) {
            if (inputBytes.length > i)
                bytes[i] = inputBytes[i];
            else
                bytes[i] = 0x00;
        }
        return Utils.HEX.encode(bytes);
    }
}

class MockBsqBlockchainService extends BsqBlockchainRpcService {
    private static final Logger log = LoggerFactory.getLogger(MockBsqBlockchainService.class);
    private List<Block> blocks;
    private int chainHeadHeight;
    private String genesisTxId;
    private int genesisBlockHeight;
    private Map<String, RawTransaction> txsByIsMap = new HashMap<>();
    private Map<Integer, List<RawTransaction>> txsInBlockMap = new HashMap<>();
    private Map<Integer, List<String>> txIdsInBlockMap = new HashMap<>();

    public MockBsqBlockchainService() {
        super(null, null, null, null, null);
    }

    public void buildBlocks(int from, int to) {
        this.chainHeadHeight = to;
        blocks = new ArrayList<>();
        for (int blockIndex = from; blockIndex <= to; blockIndex++) {
            blocks.add(getBlock(blockIndex, txIdsInBlockMap.get(blockIndex)));
        }
    }

    public void addTxToBlock(int blockHeight, RawTransaction transaction) {
        List<RawTransaction> txs;
        if (txsInBlockMap.containsKey(blockHeight)) {
            txs = txsInBlockMap.get(blockHeight);
        } else {
            txs = new ArrayList<>();
            txsInBlockMap.put(blockHeight, txs);
        }
        txs.add(transaction);

        List<String> ids;
        if (txIdsInBlockMap.containsKey(blockHeight)) {
            ids = txIdsInBlockMap.get(blockHeight);
        } else {
            ids = new ArrayList<>();
            txIdsInBlockMap.put(blockHeight, ids);
        }
        String txId = transaction.getTxId();
        ids.add(txId);

        txsByIsMap.put(txId, transaction);
    }

    public void buildTxList(int from, int to) {
        blocks = new ArrayList<>();
        for (int blockIndex = from; blockIndex < to; blockIndex++) {
            blocks.add(getBlock(blockIndex, getTxList(blockIndex)));
        }
    }

    private Block getBlock(int blockIndex, List<String> txList) {
        Block block = new Block();
        block.setHeight(blockIndex);
        block.setHash("hash" + blockIndex);
        block.setTx(txList);
        return block;
    }

    private List<String> getTxList(int blockIndex) {
        List<String> txList = new ArrayList<>();
        if (blockIndex == genesisBlockHeight) {
            txList.add(genesisTxId);
        }
        return txList;
    }

    @Override
    int requestChainHeadHeight() throws BitcoindException, CommunicationException {
        return chainHeadHeight;
    }

    @Override
    Block requestBlock(int index) throws BitcoindException, CommunicationException {
        return blocks.get(index);
    }

    public void setGenesisTx(String genesisTxId, int genesisBlockHeight) {
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
    }

    @Override
    protected RawTransaction getRawTransaction(String txId) throws BitcoindException, CommunicationException {
        return txsByIsMap.get(txId);
    }

}
