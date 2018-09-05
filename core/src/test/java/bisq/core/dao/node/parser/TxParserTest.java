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

import bisq.core.dao.node.parser.exceptions.InvalidGenesisTxException;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.RawTx;
import bisq.core.dao.state.blockchain.RawTxOutput;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;

import org.bitcoinj.core.Coin;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

public class TxParserTest {
    @Test
    public void testGetBisqTxType() {
        long time = 1371729865; // Thu Jun 20 14:04:25 CEST 2013
        final List<TxInput> inputs = Arrays.asList(
                new TxInput("tx0", 0, null),
                new TxInput("tx1", 1, null)
        );
        RawTxOutput output = new RawTxOutput(
                0,
                123,
                null,
                null,
                null,
                null,
                100
        );
        RawTx rawTx = new RawTx(
                "faketx0",
                100,
                "fakeblock0",
                time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(Arrays.asList(output))
        );
        TempTx tempTx = TempTx.fromRawTx(rawTx);
        boolean hasOpReturnCandidate = true;
        long remainingInputValue = 0;
        Optional<OpReturnType> optionalOpReturnType = Optional.empty();

        TxType result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        TxType want = TxType.INVALID;
        Assert.assertEquals(
                "With an OP_RETURN candidate but no optional OP_RETURN type, this tx should be invalid.",
                want,
                result);

        hasOpReturnCandidate = false;
        result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        want = TxType.TRANSFER_BSQ;
        Assert.assertEquals(
                "With no OP_RETURN candidate and no optional OP_RETURN type, this should be a BSQ transfer tx.",
                want,
                result
        );

        // todo(chirhonul): this is very likely incorrect, we should see the tx as INVALID if
        // !hasOpReturnCandidate but optionalOpReturnType.
        hasOpReturnCandidate = false;
        optionalOpReturnType = Optional.of(OpReturnType.LOCKUP);
        result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        want = TxType.LOCKUP;
        Assert.assertEquals(
                "With no OP_RETURN candidate and optional OP_RETURN type of LOCKUP, this should be a LOCKUP tx.",
                want,
                result
        );

        hasOpReturnCandidate = true;
        optionalOpReturnType = Optional.of(OpReturnType.BLIND_VOTE);
        result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        want = TxType.BLIND_VOTE;
        Assert.assertEquals(
                "With OP_RETURN candidate and optional OP_RETURN type of BLIND_VOTE, this should be a BLIND_VOTE tx.",
                want,
                result
        );

        hasOpReturnCandidate = true;
        optionalOpReturnType = Optional.of(OpReturnType.VOTE_REVEAL);
        result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        want = TxType.VOTE_REVEAL;
        Assert.assertEquals(
                "With OP_RETURN candidate and optional OP_RETURN type of VOTE_REVEAL, this should be a VOTE_REVEAL tx.",
                want,
                result
        );

        hasOpReturnCandidate = true;
        optionalOpReturnType = Optional.of(OpReturnType.PROPOSAL);
        result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        want = TxType.PROPOSAL;
        Assert.assertEquals(
                "With OP_RETURN candidate and optional OP_RETURN type of PROPOSAL, this should be a PROPOSAL tx.",
                want,
                result
        );

        hasOpReturnCandidate = true;
        optionalOpReturnType = Optional.of(OpReturnType.COMPENSATION_REQUEST);
        result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        want = TxType.INVALID;
        Assert.assertEquals(
                "COMPENSATION_REQUEST has fewer than three outputs, this should be a INVALID tx.",
                want,
                result
        );

        RawTxOutput output1 = new RawTxOutput(
                0,
                123,
                null,
                null,
                null,
                null,
                100
        );
        RawTxOutput output2 = new RawTxOutput(
                0,
                456,
                null,
                null,
                null,
                null,
                100
        );
        RawTxOutput output3 = new RawTxOutput(
                0,
                678,
                null,
                null,
                null,
                null,
                100
        );
        rawTx = new RawTx(
                "faketx1",
                200,
                "fakeblock1",
                time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(Arrays.asList(output1, output2, output3))
        );
        tempTx = TempTx.fromRawTx(rawTx);
        hasOpReturnCandidate = true;
        optionalOpReturnType = Optional.of(OpReturnType.COMPENSATION_REQUEST);

        result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        want = TxType.INVALID;
        Assert.assertEquals(
                "Output 1 at COMPENSATION_REQUEST has to be a ISSUANCE_CANDIDATE_OUTPUT, this should be a INVALID tx.",
                want,
                result
        );

        hasOpReturnCandidate = true;
        optionalOpReturnType = Optional.of(OpReturnType.COMPENSATION_REQUEST);
        tempTx.getTempTxOutputs().get(1).setTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
        result = TxParser.getBisqTxType(
                tempTx,
                hasOpReturnCandidate,
                remainingInputValue,
                optionalOpReturnType);
        want = TxType.COMPENSATION_REQUEST;
        Assert.assertEquals(
                "With OP_RETURN candidate and optional OP_RETURN type of COMPENSATION_REQUEST, this should be a COMPENSATION_REQUEST tx.",
                want,
                result
        );
    }

    @Test
    public void testGetGenesisTx() {
        int blockHeight = 200;
        String blockHash = "abc123";
        Coin genesisTotalSupply = Coin.parseCoin("2.5");
        long time = 1371729865; // Thu Jun 20 14:04:25 CEST 2013
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
        Optional<TempTx> result = TxParser.findGenesisTx(genesisTxId, genesisBlockHeight, genesisTotalSupply, rawTx);
        Optional<TempTx> want = Optional.empty();
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
        result = TxParser.findGenesisTx(genesisTxId, genesisBlockHeight, genesisTotalSupply, rawTx);
        want = Optional.empty();
        Assert.assertEquals(want, result);

        // With correct tx id and block height, we should find our genesis tx with correct tx and output type.
        rawTx = new RawTx(
                genesisTxId,
                blockHeight,
                blockHash,
                time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(Arrays.asList(output))
        );
        result = TxParser.findGenesisTx(genesisTxId, genesisBlockHeight, genesisTotalSupply, rawTx);

        TempTx tempTx = TempTx.fromRawTx(rawTx);
        tempTx.setTxType(TxType.GENESIS);
        for (int i = 0; i < tempTx.getTempTxOutputs().size(); ++i) {
            tempTx.getTempTxOutputs().get(i).setTxOutputType(TxOutputType.GENESIS_OUTPUT);
        }
        want = Optional.of(tempTx);

        Assert.assertEquals(want, result);

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
            result = TxParser.findGenesisTx(genesisTxId, genesisBlockHeight, genesisTotalSupply, rawTx);
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
            result = TxParser.findGenesisTx(genesisTxId, genesisBlockHeight, genesisTotalSupply, rawTx);
            Assert.fail("Expected an InvalidGenesisTxException to be thrown when outputs are too high");
        } catch (InvalidGenesisTxException igtxe) {
            String wantMessage = "Genesis tx is invalid; using more than available inputs. Remaining input value is 2 sat";
            Assert.assertTrue("Unexpected exception, want message starting with " +
                    "'" + wantMessage + "', got '" + igtxe.getMessage() + "'", igtxe.getMessage().startsWith(wantMessage));
        }
    }
}
