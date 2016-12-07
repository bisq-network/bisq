/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.tokens;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class TransactionParserTest {
    private static final Logger log = LoggerFactory.getLogger(TransactionParserTest.class);

    private MockTxService txService;
    private TransactionParser transactionParser;

    private Tx genesisTx;
    private Tx tx1;
    private TxOutput output1;
    private TxOutput output2;
    private TxInput input1;
    private TxOutput output1_1;
    private TxInput input2;
    private TxOutput output2_1;
    private TxInput genesisInput;

    @Before
    public void setup() {
        txService = new MockTxService();
        transactionParser = new TransactionParser("id_genesis", txService);
    }

    @After
    public void tearDown() {
        txService.cleanup();
    }

    @Test
    public void testGetTx() {
        assertEquals(createGenesisTx(), transactionParser.getTx("id_genesis"));
        assertEquals(createTx1(), transactionParser.getTx("id_tx1"));
    }

    @Test
    public void testValidTxs() {
        transactionParser.applyIsTokenForAllOutputs(createGenesisTx());
        assertTrue(transactionParser.isValidInput(genesisInput));
        assertTrue(transactionParser.isValidOutput(output1));
        assertTrue(transactionParser.isValidOutput(output1));

        transactionParser.applyIsTokenForAllOutputs(createTx1());
        assertTrue(transactionParser.isValidInput(input1));
        assertFalse(transactionParser.isValidOutput(output1));
        assertTrue(transactionParser.isValidOutput(output1_1));
        assertTrue(transactionParser.isValidOutput(output2));

        transactionParser.applyIsTokenForAllOutputs(createTx2());
        assertTrue(transactionParser.isValidInput(input1));
        assertTrue(transactionParser.isValidInput(input2));
        assertFalse(transactionParser.isValidOutput(output1));
        assertTrue(transactionParser.isValidOutput(output1_1));
        assertFalse(transactionParser.isValidOutput(output2));
        assertTrue(transactionParser.isValidOutput(output2_1));

    }

    @Test
    public void testGetAllUTXOs() {
        Tx genesisTx = createGenesisTx();
        transactionParser.applyIsTokenForAllOutputs(genesisTx);
        List<TxOutput> allUTXOs = transactionParser.getAllUTXOs(genesisTx);
        assertEquals(2, allUTXOs.size());
        allUTXOs.stream().forEach(output -> {
            log.debug("output " + output);
            assertTrue(transactionParser.isValidOutput(output));
        });

        transactionParser.applyIsTokenForAllOutputs(createTx1());
        allUTXOs = transactionParser.getAllUTXOs(genesisTx);
        assertEquals(2, allUTXOs.size());
        allUTXOs.stream().forEach(output -> {
            log.debug("output " + output);
            assertTrue(transactionParser.isValidOutput(output));
        });

        transactionParser.applyIsTokenForAllOutputs(createTx2());
        allUTXOs = transactionParser.getAllUTXOs(genesisTx);
        assertEquals(2, allUTXOs.size());
        allUTXOs.stream().forEach(output -> {
            log.debug("output " + output);
            assertTrue(transactionParser.isValidOutput(output));
        });
    }


    @Test
    public void testInvalidTxs() {
        transactionParser.applyIsTokenForAllOutputs(createGenesisTx());

        transactionParser.applyIsTokenForAllOutputs(createInvalidTx1_tooHighValue());
        assertTrue(transactionParser.isValidInput(input1));
        assertFalse(transactionParser.isValidOutput(output1)); // spent

        assertFalse(transactionParser.isValidOutput(output1_1)); // to high value
        assertTrue(transactionParser.isValidOutput(output2));


        transactionParser.applyIsTokenForAllOutputs(createTx1());
        assertTrue(transactionParser.isValidInput(input1));
        assertFalse(transactionParser.isValidOutput(output1));
        assertTrue(transactionParser.isValidOutput(output1_1));
        assertTrue(transactionParser.isValidOutput(output2));
    }

    private Tx createGenesisTx() {
        Tx tx = new Tx("id_genesis");

        genesisInput = new TxInput(new Tx("id_genesisInput", null, null), null);
        genesisInput.value = 10_000;
        tx.addInput(genesisInput);

        output1 = new TxOutput("addr_1", 1000);
        tx.addOutput(output1);

        output2 = new TxOutput("addr_2", 2000);
        tx.addOutput(output2);

        txService.addTx(tx);
        return tx;
    }

    private Tx createTx1() {
        Tx tx = new Tx("id_tx1");

        input1 = new TxInput(genesisTx, output1);
        tx.addInput(input1);

        TxInput feeInput = new TxInput(new Tx("id_fee_1", null, null), null);
        tx.addInput(feeInput);

        output1_1 = new TxOutput("addr_1_1", 1000);
        tx.addOutput(output1_1);

        txService.addTx(tx);
        return tx;
    }

    private Tx createTx2() {
        Tx tx = new Tx("id_tx2");

        input2 = new TxInput(genesisTx, output2);
        tx.addInput(input2);

        TxInput feeInput = new TxInput(new Tx("id_fee_2", null, null), null);
        tx.addInput(feeInput);

        output2_1 = new TxOutput("addr_2_1", 1000);
        tx.addOutput(output2_1);

        txService.addTx(tx);
        return tx;
    }

    private Tx createInvalidTx1_tooHighValue() {
        Tx tx = new Tx("id_tx1");

        input1 = new TxInput(genesisTx, output1);
        tx.addInput(input1);

        TxInput feeInput = new TxInput(new Tx("id_fee_1", null, null), null);
        tx.addInput(feeInput);

        output1_1 = new TxOutput("addr_1_1", 4000);
        tx.addOutput(output1_1);

        txService.addTx(tx);
        return tx;
    }
}
