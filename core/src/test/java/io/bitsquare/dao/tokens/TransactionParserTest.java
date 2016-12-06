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

import org.bitcoinj.core.Coin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TransactionParserTest {
    private Tx genesisTx;
    private MockTxService txService;
    private TransactionParser transactionParser;
    private TxOutput output1;
    private TxOutput output2;
    private TxInput input1;

    @Before
    public void setup() {
        genesisTx = getGenesisTx();
        txService = new MockTxService(genesisTx);
        transactionParser = new TransactionParser("123", txService);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGetTx() {
        Tx genesisTxResult = transactionParser.getTx("id_genesis");
        assertEquals(genesisTx, genesisTxResult);
    }

    @Test
    public void testTx1() {
        Tx genesisTxResult = transactionParser.getTx("id_genesis");
        assertEquals(genesisTx, genesisTxResult);
    }

    private Tx getGenesisTx() {
        Tx tx = new Tx("id_genesis");

        tx.addInput(new TxInput(new Tx("id_0001", null, null), null, Coin.COIN.value));

        output1 = new TxOutput("addr_1", 1000);
        tx.addOutput(output1);

        output2 = new TxOutput("addr_2", 2000);
        tx.addOutput(output2);

        return tx;
    }

    private Tx getTx1() {
        Tx tx = new Tx("id_tx1");

        input1 = new TxInput(genesisTx, output1, Coin.COIN.value);
        tx.addInput(input1);
        
        TxInput feeInput = new TxInput(new Tx("id_0001", null, null), null, 20_000);
        Tx feeTx = new Tx("id_fee_1", null, null);

        tx.addOutput(new TxOutput("addr_1", 1000));
        tx.addOutput(new TxOutput("addr_2", 2000));
        return tx;
    }
}
