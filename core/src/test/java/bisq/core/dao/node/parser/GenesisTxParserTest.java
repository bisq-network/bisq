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

package bisq.core.dao.node.parser;

import bisq.core.dao.node.full.RawTx;
import bisq.core.dao.node.full.RawTxOutput;
import bisq.core.dao.node.parser.exceptions.InvalidGenesisTxException;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;

import org.bitcoinj.core.Coin;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class GenesisTxParserTest {

    @Test
    public void testIsGenesis() {
        // fixme(chirhonul): Assert.assertEquals(2, 3);

        int blockHeight = 200;
        String blockHash = "abc123";
        Coin genesisTotalSupply = Coin.parseCoin("2.5");
        long time = new Date().getTime();
        final List<TxInput> inputs = Arrays.asList(
                new TxInput("tx0", 0, null),
                new TxInput("tx1", 1, null)
        );
        RawTxOutput output = new RawTxOutput(
                0,
                genesisTotalSupply.value,
                null,
                null,
                null,
                null,
                blockHeight
        );
        RawTx rawTx = new RawTx(
                "tx2",
                blockHeight,
                blockHash,
                time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(Arrays.asList(output))
        );

        String genesisTxId = "genesisTxId";
        int genesisBlockHeight = 150;

        // With mismatch in block height and tx id, we should not get genesis tx back.
        boolean result = GenesisTxParser.isGenesis(rawTx, genesisTxId, genesisBlockHeight);
        boolean want = false;
        Assert.assertEquals(want, result);

        // With correct block height but mismatch in tx id, we should still not get genesis tx back.
        blockHeight = 150;
        rawTx = new RawTx(
                "tx2",
                blockHeight,
                blockHash,
                time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(Arrays.asList(output))
        );
        result = GenesisTxParser.isGenesis(rawTx, genesisTxId, genesisBlockHeight);
        want = false;
        Assert.assertEquals(want, result);

    }

    @Test
    public void testGetGenesisTempTx() {
        int blockHeight = 200;
        String blockHash = "abc123";
        Coin genesisTotalSupply = Coin.parseCoin("2.5");
        long time = new Date().getTime();
        final List<TxInput> inputs = Arrays.asList(
                new TxInput("tx0", 0, null),
                new TxInput("tx1", 1, null)
        );
        RawTxOutput output = new RawTxOutput(
                0,
                genesisTotalSupply.value,
                null,
                null,
                null,
                null,
                blockHeight
        );

        String genesisTxId = "genesisTxId";
        // With correct tx id and block height, we should find our genesis tx with correct tx and output type.
        RawTx rawTx = new RawTx(
                genesisTxId,
                blockHeight,
                blockHash,
                time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(Arrays.asList(output))
        );
        TempTx resultTempTx = GenesisTxParser.getGenesisTempTx(rawTx, genesisTotalSupply);

        TempTx tempTx = TempTx.fromRawTx(rawTx);
        tempTx.setTxType(TxType.GENESIS);
        for (int i = 0; i < tempTx.getTempTxOutputs().size(); ++i) {
            tempTx.getTempTxOutputs().get(i).setTxOutputType(TxOutputType.GENESIS_OUTPUT);
        }
        TempTx wantTempTx = tempTx;

        Assert.assertEquals(wantTempTx, resultTempTx);

        // With correct tx id and block height, but too low sum of outputs (lower than genesisTotalSupply), we
        // should see an exception raised.
        output = new RawTxOutput(
                0,
                genesisTotalSupply.value - 1,
                null,
                null,
                null,
                null,
                blockHeight
        );
        rawTx = new RawTx(
                genesisTxId,
                blockHeight,
                blockHash,
                time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(Arrays.asList(output))
        );
        try {
            GenesisTxParser.getGenesisTempTx(rawTx, genesisTotalSupply);
            Assert.fail("Expected an InvalidGenesisTxException to be thrown when outputs are too low");
        } catch (InvalidGenesisTxException igtxe) {
            String wantMessage = "Genesis tx is invalid; not using all available inputs. Remaining input value is 1 sat";
            Assert.assertTrue("Unexpected exception, want message starting with " +
                    "'" + wantMessage + "', got '" + igtxe.getMessage() + "'", igtxe.getMessage().startsWith(wantMessage));
        }

        // With correct tx id and block height, but too high sum of outputs (higher than from genesisTotalSupply), we
        // should see an exception raised.
        RawTxOutput output1 = new RawTxOutput(
                0,
                genesisTotalSupply.value - 2,
                null,
                null,
                null,
                null,
                blockHeight
        );
        RawTxOutput output2 = new RawTxOutput(
                0,
                3,
                null,
                null,
                null,
                null,
                blockHeight
        );
        rawTx = new RawTx(
                genesisTxId,
                blockHeight,
                blockHash,
                time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(Arrays.asList(output1, output2))
        );
        try {
            GenesisTxParser.getGenesisTempTx(rawTx, genesisTotalSupply);
            Assert.fail("Expected an InvalidGenesisTxException to be thrown when outputs are too high");
        } catch (InvalidGenesisTxException igtxe) {
            String wantMessage = "Genesis tx is invalid; using more than available inputs. Remaining input value is 2 sat";
            Assert.assertTrue("Unexpected exception, want message starting with " +
                    "'" + wantMessage + "', got '" + igtxe.getMessage() + "'", igtxe.getMessage().startsWith(wantMessage));
        }
    }
}
