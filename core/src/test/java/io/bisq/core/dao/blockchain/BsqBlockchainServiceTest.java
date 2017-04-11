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
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

import static org.junit.Assert.*;

@Slf4j
public class BsqBlockchainServiceTest {


    public static final int BLOCK_0 = 0;
    public static final int BLOCK_1 = 1;
    public static final int BLOCK_2 = 2;

    public static final String FUND_GEN_FUND_TX_ID = "FUND_GEN_FUND_TX_ID";
    public static final String FUND_GEN_TX_ID = "FUND_GEN_TX_ID";
    public static final String GEN_TX_ID = "GEN_TX_ID";
    public static final String TX1_ID = "TX1_ID";
    public static final String TX2_ID = "TX2_ID";

    public static final String ADDRESS_GEN_FUND_TX = "ADDRESS_GEN_FUND_TX";
    public static final String ADDRESS_GEN_1 = "ADDRESS_GEN_1";
    public static final String ADDRESS_GEN_2 = "ADDRESS_GEN_2";
    public static final String ADDRESS_TX_1 = "ADDRESS_TX_1";
    public static final String ADDRESS_TX_2 = "ADDRESS_TX_2";

    public static final long ADDRESS_GEN_1_VALUE = Coin.parseCoin("0.00005000").value;
    public static final long ADDRESS_GEN_2_VALUE = Coin.parseCoin("0.00001000").value;
    public static final long ADDRESS_TX_1_VALUE = Coin.parseCoin("0.00001000").value;
    public static final long ADDRESS_TX_2_VALUE = Coin.parseCoin("0.00001000").value;

    private MockBsqBlockchainService service;
    private TxOutputMap txOutputMap;

    @Before
    public void setup() {
        final URL resource = this.getClass().getClassLoader().getResource("");
        final String path = resource != null ? resource.getFile() : "";
        log.info("path for BsqUTXOMap=" + path);
        txOutputMap = new TxOutputMap(new File(path), null);
        service = new MockBsqBlockchainService();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGenTx() throws BsqBlockchainException, BitcoindException, CommunicationException {
        // GENESIS_TX (block 0):
        // Input 0: output from GEN_FUNDING_TX_ID
        // Output 0: ADDRESS_GEN_1 ADDRESS_GEN_1_VALUE
        // Output 1: ADDRESS_GEN_2 ADDRESS_GEN_2_VALUE

        // UTXO:
        // GENESIS_TX_ID:0
        // GENESIS_TX_ID:1

        buildGenesisBlock();

        service.buildBlocks(BLOCK_0, BLOCK_0);

        parseAllBlocksFromGenesis();

        TxOutput bsqTxo1 = txOutputMap.get(GEN_TX_ID, 0);
        TxOutput bsqTxo2 = txOutputMap.get(GEN_TX_ID, 1);
        assertEquals(bsqTxo1.getTxoId(), getTxoId(GEN_TX_ID, 0));
        assertEquals(bsqTxo2.getTxoId(), getTxoId(GEN_TX_ID, 1));
        assertEquals(ADDRESS_GEN_1_VALUE, bsqTxo1.getValue());
        assertEquals(ADDRESS_GEN_2_VALUE, bsqTxo2.getValue());
        assertEquals(2, txOutputMap.size());
    }


    @Test
    public void testGenToTx1() throws BsqBlockchainException, BitcoindException, CommunicationException {
        // GENESIS_TX (block 0):
        // Input 0: Output 0 from GEN_FUNDING_TX_ID
        // Output 0: ADDRESS_GEN_1 ADDRESS_GEN_1_VALUE
        // Output 1: ADDRESS_GEN_2 ADDRESS_GEN_2_VALUE

        // TX1 (block 1):
        // Input 0: Output 1 from GENESIS_TX
        // Output 0: ADDRESS_TX_1 ADDRESS_TX_1_VALUE (=ADDRESS_GEN_2_VALUE)

        // UTXO:
        // GENESIS_TX_ID:0
        // TX1_ID:0

        buildGenesisBlock();

        buildTx(GEN_TX_ID,
                1,
                TX1_ID,
                BLOCK_1,
                0,
                ADDRESS_TX_1_VALUE,
                ADDRESS_TX_1);

        service.buildBlocks(BLOCK_0, BLOCK_1);

        parseAllBlocksFromGenesis();

        TxOutput bsqTxo1 = txOutputMap.get(GEN_TX_ID, 0);
        TxOutput bsqTxo2 = txOutputMap.get(TX1_ID, 0);
        assertTrue(bsqTxo1.isUnSpend());
        assertTrue(bsqTxo2.isUnSpend());
        assertEquals(bsqTxo1.getTxoId(), getTxoId(GEN_TX_ID, 0));
        assertEquals(bsqTxo2.getTxoId(), getTxoId(TX1_ID, 0));
        assertEquals(ADDRESS_GEN_1_VALUE, bsqTxo1.getValue());
        assertEquals(ADDRESS_TX_1_VALUE, bsqTxo2.getValue());
        assertEquals(3, txOutputMap.size());
    }

    @Test
    public void testGenToTx1ToTx2InBlock1() throws BsqBlockchainException, BitcoindException, CommunicationException {
        // GENESIS_TX (block 0):
        // Input 0: Output 0 from GEN_FUNDING_TX_ID
        // Output 0: ADDRESS_GEN_1 ADDRESS_GEN_1_VALUE
        // Output 1: ADDRESS_GEN_2 ADDRESS_GEN_2_VALUE

        // TX1 (block 1):
        // Input 0: Output 1 from GENESIS_TX
        // Output 0: ADDRESS_TX_1 ADDRESS_TX_1_VALUE (=ADDRESS_GEN_2_VALUE)

        // TX2 (block 1):
        // Input 0: Output 0 from TX1
        // Output 0: ADDRESS_TX_2 ADDRESS_TX_2_VALUE (=ADDRESS_TX_1_VALUE)

        // UTXO:
        // GENESIS_TX_ID:0
        // TX2_ID:0

        buildGenesisBlock();

        // Tx1 uses as input the output 1 of genTx
        buildTx(GEN_TX_ID,
                1,
                TX1_ID,
                BLOCK_1,
                0,
                ADDRESS_TX_1_VALUE,
                ADDRESS_TX_1);

        // Tx2 uses as input the output 0 of Tx1
        buildTx(TX1_ID,
                0,
                TX2_ID,
                BLOCK_1,
                0,
                ADDRESS_TX_2_VALUE,
                ADDRESS_TX_2);

        service.buildBlocks(BLOCK_0, BLOCK_1);

        parseAllBlocksFromGenesis();

        TxOutput bsqTxo1 = txOutputMap.get(GEN_TX_ID, 0);
        TxOutput bsqTxo2 = txOutputMap.get(TX2_ID, 0);

        txOutputMap.values().forEach(e -> {
            if (e.equals(bsqTxo1) || e.equals(bsqTxo2))
                assertTrue(e.isUnSpend());
            else
                assertFalse(e.isUnSpend());
        });
        assertEquals(bsqTxo1.getTxoId(), getTxoId(GEN_TX_ID, 0));
        assertEquals(bsqTxo2.getTxoId(), getTxoId(TX2_ID, 0));
        assertEquals(ADDRESS_GEN_1_VALUE, bsqTxo1.getValue());
        assertEquals(ADDRESS_TX_2_VALUE, bsqTxo2.getValue());
        assertEquals(4, txOutputMap.size());
    }

    @Test
    public void testGenToTx1ToTx2InBlock2() throws BsqBlockchainException, BitcoindException, CommunicationException {
        // GENESIS_TX (block 0):
        // Input 0: Output 0 from GEN_FUNDING_TX_ID
        // Output 0: ADDRESS_GEN_1 ADDRESS_GEN_1_VALUE
        // Output 1: ADDRESS_GEN_2 ADDRESS_GEN_2_VALUE

        // TX1 (block 1):
        // Input 0: Output 1 from GENESIS_TX
        // Output 0: ADDRESS_TX_1 ADDRESS_TX_1_VALUE (=ADDRESS_GEN_2_VALUE)

        // TX2 (block 2):
        // Input 0: Output 0 from TX1
        // Output 0: ADDRESS_TX_2 ADDRESS_TX_2_VALUE (=ADDRESS_TX_1_VALUE)

        // UTXO:
        // GENESIS_TX_ID:0
        // TX2_ID:0

        buildGenesisBlock();

        // Tx1 uses as input the output 1 of genTx
        buildTx(GEN_TX_ID,
                1,
                TX1_ID,
                BLOCK_1,
                0,
                ADDRESS_TX_1_VALUE,
                ADDRESS_TX_1);

        // Tx2 uses as input the output 0 of Tx1
        buildTx(TX1_ID,
                0,
                TX2_ID,
                BLOCK_2,
                0,
                ADDRESS_TX_2_VALUE,
                ADDRESS_TX_2);

        service.buildBlocks(BLOCK_0, BLOCK_2);

        parseAllBlocksFromGenesis();

        TxOutput bsqTxo1 = txOutputMap.get(GEN_TX_ID, 0);
        TxOutput bsqTxo2 = txOutputMap.get(TX2_ID, 0);
        txOutputMap.values().forEach(e -> {
            if (e.equals(bsqTxo1) || e.equals(bsqTxo2))
                assertTrue(e.isUnSpend());
            else
                assertFalse(e.isUnSpend());
        });
        assertEquals(bsqTxo1.getTxoId(), getTxoId(GEN_TX_ID, 0));
        assertEquals(bsqTxo2.getTxoId(), getTxoId(TX2_ID, 0));
        assertEquals(ADDRESS_GEN_1_VALUE, bsqTxo1.getValue());
        assertEquals(ADDRESS_TX_2_VALUE, bsqTxo2.getValue());
        assertEquals(4, txOutputMap.size());
    }

    @Test
    public void testGenToTx1ToTx2AndGenToTx2InBlock1() throws BsqBlockchainException, BitcoindException, CommunicationException {
        // GENESIS_TX (block 0):
        // Input 0: Output 0 from GEN_FUNDING_TX_ID
        // Output 0: ADDRESS_GEN_1 ADDRESS_GEN_1_VALUE
        // Output 1: ADDRESS_GEN_2 ADDRESS_GEN_2_VALUE

        // TX1 (block 1):
        // Input 0: Output 1 from GENESIS_TX
        // Output 0: ADDRESS_TX_1 ADDRESS_TX_1_VALUE (=ADDRESS_GEN_2_VALUE)

        // TX2 (block 1):
        // Input 0: Output 0 from TX1
        // Input 1: Output 0 from GENESIS_TX
        // Output 0: ADDRESS_TX_2 ADDRESS_TX_1_VALUE + ADDRESS_GEN_1_VALUE

        // UTXO:
        // TX2_ID:0

        buildGenesisBlock();

        // Tx1 uses as input the output 1 of genTx
        buildTx(GEN_TX_ID,
                1,
                TX1_ID,
                BLOCK_1,
                0,
                ADDRESS_TX_1_VALUE,
                ADDRESS_TX_1);

        // Tx2 uses as input the output 0 of Tx1 and output 0 of genTx
        List<RawInput> rawInputs = new ArrayList<>();
        rawInputs.add(getRawInput(0, TX1_ID));
        rawInputs.add(getRawInput(0, GEN_TX_ID));
        RawTransaction rawTransaction = getRawTransaction(BLOCK_1, TX2_ID);
        rawTransaction.setVIn(rawInputs);
        List<RawOutput> rawOutputs = new ArrayList<>();
        rawOutputs.add(getRawOutput(0, ADDRESS_TX_1_VALUE + ADDRESS_GEN_1_VALUE, ADDRESS_TX_2));
        rawTransaction.setVOut(rawOutputs);
        service.addTxToBlock(BLOCK_1, rawTransaction);

        service.buildBlocks(BLOCK_0, BLOCK_1);

        parseAllBlocksFromGenesis();

        TxOutput bsqTxo1 = txOutputMap.get(TX2_ID, 0);
        txOutputMap.values().forEach(e -> {
            if (e.equals(bsqTxo1))
                assertTrue(e.isUnSpend());
            else
                assertFalse(e.isUnSpend());
        });
        assertEquals(bsqTxo1.getTxoId(), getTxoId(TX2_ID, 0));
        assertEquals(ADDRESS_GEN_1_VALUE + ADDRESS_GEN_2_VALUE, bsqTxo1.getValue());
        assertEquals(4, txOutputMap.size());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get btcd objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RawTransaction getRawTransaction(int height, String txId) {
        RawTransaction genesisRawTransaction = new RawTransaction();
        genesisRawTransaction.setBlockHash("BlockHash" + height);
        genesisRawTransaction.setTxId(txId);
        genesisRawTransaction.setTime(new Date().getTime() / 1000);
        return genesisRawTransaction;
    }

    private RawOutput getRawOutput(int index, long value, String address) {
        RawOutput rawOutput = new RawOutput();
        rawOutput.setN(index);
        rawOutput.setValue(BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100000000)));
        PubKeyScript scriptPubKey = new PubKeyScript();
        scriptPubKey.setAddresses(Collections.singletonList(address));
        rawOutput.setScriptPubKey(scriptPubKey);
        return rawOutput;
    }

    private RawInput getRawInput(int index, String spendingTxId) {
        RawInput rawInput = new RawInput();
        rawInput.setTxId(spendingTxId);
        rawInput.setVOut(index);
        return rawInput;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void buildGenesisBlock()
            throws BsqBlockchainException, BitcoindException, CommunicationException {
        // tx funding the funding tx for genesis tx
        List<RawInput> inputForFundGenTx = new ArrayList<>();
        inputForFundGenTx.add(getRawInput(0, FUND_GEN_FUND_TX_ID));
        final RawTransaction fundGenTx = getRawTransaction(BLOCK_0, FUND_GEN_TX_ID);
        fundGenTx.setVIn(inputForFundGenTx);

        List<RawOutput> outputForFundGenTx = new ArrayList<>();
        outputForFundGenTx.add(getRawOutput(0, ADDRESS_GEN_1_VALUE + ADDRESS_GEN_2_VALUE, ADDRESS_GEN_FUND_TX));
        fundGenTx.setVOut(outputForFundGenTx);

        service.addTxToBlock(BLOCK_0, fundGenTx);

        List<RawInput> inputsForGenTx = new ArrayList<>();
        inputsForGenTx.add(getRawInput(0, FUND_GEN_TX_ID));
        RawTransaction genesisTx = getRawTransaction(BLOCK_0, GEN_TX_ID);
        genesisTx.setVIn(inputsForGenTx);

        List<RawOutput> outputs = new ArrayList<>();
        outputs.add(getRawOutput(0, ADDRESS_GEN_1_VALUE, ADDRESS_GEN_1));
        outputs.add(getRawOutput(1, ADDRESS_GEN_2_VALUE, ADDRESS_GEN_2));
        genesisTx.setVOut(outputs);

        service.setGenesisTx(GEN_TX_ID, BLOCK_0);
        service.addTxToBlock(BLOCK_0, genesisTx);
        service.buildBlocks(BLOCK_0, BLOCK_0);
    }

    private void buildTx(String spendingTxId,
                         int spendingTxOutputIndex,
                         String txId,
                         int txBlockHeight,
                         int outputIndex,
                         long outputValue,
                         String outputAddress) {
        List<RawInput> rawInputs = new ArrayList<>();
        rawInputs.add(getRawInput(spendingTxOutputIndex, spendingTxId));

        RawTransaction rawTransaction = getRawTransaction(txBlockHeight, txId);
        rawTransaction.setVIn(rawInputs);

        List<RawOutput> rawOutputs = new ArrayList<>();
        rawOutputs.add(getRawOutput(outputIndex, outputValue, outputAddress));
        rawTransaction.setVOut(rawOutputs);

        service.addTxToBlock(txBlockHeight, rawTransaction);
    }


    private void parseAllBlocksFromGenesis()
            throws BitcoindException, CommunicationException, BsqBlockchainException {
        BsqParser bsqParser = new BsqParser(service);
        bsqParser.parseBlocks(BLOCK_0,
                service.requestChainHeadHeight(),
                BLOCK_0,
                GEN_TX_ID,
                txOutputMap);
    }

    private String getTxoId(String txId, int index) {
        return txId + ":" + index;
    }
}

///////////////////////////////////////////////////////////////////////////////////////////
// Mock
///////////////////////////////////////////////////////////////////////////////////////////

@Slf4j
class MockBsqBlockchainService extends BsqBlockchainRpcService {
    private List<Block> blocks;
    private int chainHeadHeight;
    private String GENESIS_TX_ID;
    private int GENESIS_HEIGHT;
    private final Map<String, RawTransaction> txByIdMap = new HashMap<>();
    private final Map<Integer, List<RawTransaction>> txsInBlockMap = new HashMap<>();
    private final Map<Integer, List<String>> txIdsInBlockMap = new HashMap<>();

    public MockBsqBlockchainService() {
        super(null, null, null, null);
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

        txByIdMap.put(txId, transaction);
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
        if (blockIndex == GENESIS_HEIGHT) {
            txList.add(GENESIS_TX_ID);
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

    public void setGenesisTx(String GENESIS_TX_ID, int GENESIS_HEIGHT) {
        this.GENESIS_TX_ID = GENESIS_TX_ID;
        this.GENESIS_HEIGHT = GENESIS_HEIGHT;
    }

    @Override
    RawTransaction requestRawTransaction(String txId) throws BitcoindException, CommunicationException {
        return txByIdMap.get(txId);
    }
}
