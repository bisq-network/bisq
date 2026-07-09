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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.full;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RawBlockCanonicalEncoderTest {

    @Test
    public void canonicalEncodingIsDeterministicForEqualContent() {
        RawBlock first = RawBlock.fromProto(sampleBlockProto());
        RawBlock second = RawBlock.fromProto(sampleBlockProto());

        assertArrayEquals(first.encodeCanonical(), second.encodeCanonical());
    }

    @Test
    public void rawBlockRoundTripThroughProtoPreservesCanonicalEncoding() {
        RawBlock original = RawBlock.fromProto(sampleBlockProto());
        RawBlock roundTripped = RawBlock.fromProto(original.toProtoMessage());

        assertArrayEquals(original.encodeCanonical(), roundTripped.encodeCanonical());
    }

    @Test
    public void canonicalEncodingCoversTxOutputContent() {
        // Same block hash, same tx set size and ids, but a single output value differs.
        // If the canonical encoding did not cover the output value the two blocks would
        // hash to the same signable representation and a poisoned block could be swapped
        // for a benign one under a valid signature.
        protobuf.BaseTxOutput baseOutput = sampleBaseTxOutput(123_456L, "B1address");
        protobuf.BaseTxOutput tamperedOutput = sampleBaseTxOutput(999_999L, "B1address");

        RawBlock benign = RawBlock.fromProto(blockProtoWithOutput(baseOutput));
        RawBlock tampered = RawBlock.fromProto(blockProtoWithOutput(tamperedOutput));

        assertFalse(Arrays.equals(benign.encodeCanonical(), tampered.encodeCanonical()),
                "Canonical encoding must differ when a tx output value is tampered with");
    }

    @Test
    public void canonicalEncodingCoversTxOutputAddress() {
        // Changing only the payout address (e.g. re-targeting a BSQ output to an attacker
        // controlled address) must break the canonical encoding so the signed hash is
        // invalidated.
        protobuf.BaseTxOutput baseOutput = sampleBaseTxOutput(123_456L, "B1address");
        protobuf.BaseTxOutput tamperedOutput = sampleBaseTxOutput(123_456L, "B1attackerAddress");

        RawBlock benign = RawBlock.fromProto(blockProtoWithOutput(baseOutput));
        RawBlock tampered = RawBlock.fromProto(blockProtoWithOutput(tamperedOutput));

        assertFalse(Arrays.equals(benign.encodeCanonical(), tampered.encodeCanonical()),
                "Canonical encoding must differ when a tx output address is tampered with");
    }

    @Test
    public void canonicalEncodingCoversTxSet() {
        RawBlock zeroTxs = RawBlock.fromProto(protobuf.BaseBlock.newBuilder()
                .setHeight(654321)
                .setTime(1_700_000_000_000L)
                .setHash("block-hash")
                .setPreviousBlockHash("previous-block-hash")
                .setRawBlock(protobuf.RawBlock.newBuilder())
                .build());

        RawBlock extraTx = RawBlock.fromProto(sampleBlockProto());

        assertFalse(Arrays.equals(zeroTxs.encodeCanonical(), extraTx.encodeCanonical()),
                "Canonical encoding must differ when a tx is injected into the block");
    }

    private static protobuf.BaseBlock sampleBlockProto() {
        return blockProtoWithOutput(sampleBaseTxOutput(123_456L, "B1address"));
    }

    private static protobuf.BaseBlock blockProtoWithOutput(protobuf.BaseTxOutput output) {
        return protobuf.BaseBlock.newBuilder()
                .setHeight(654321)
                .setTime(1_700_000_000_000L)
                .setHash("block-hash")
                .setPreviousBlockHash("previous-block-hash")
                .setRawBlock(protobuf.RawBlock.newBuilder()
                        .addRawTxs(protobuf.BaseTx.newBuilder()
                                .setTxVersion("2")
                                .setId("tx-id")
                                .setBlockHeight(654321)
                                .setBlockHash("block-hash")
                                .setTime(1_700_000_001_000L)
                                .addTxInputs(protobuf.TxInput.newBuilder()
                                        .setConnectedTxOutputTxId("connected-tx")
                                        .setConnectedTxOutputIndex(0)
                                        .setPubKey("03abcdef"))
                                .setRawTx(protobuf.RawTx.newBuilder()
                                        .addRawTxOutputs(output))
                                .build()))
                .build();
    }

    private static protobuf.BaseTxOutput sampleBaseTxOutput(long value, String address) {
        return protobuf.BaseTxOutput.newBuilder()
                .setIndex(0)
                .setValue(value)
                .setTxId("tx-id")
                .setPubKeyScript(protobuf.PubKeyScript.newBuilder()
                        .setReqSigs(1)
                        .setScriptType(protobuf.ScriptType.PUB_KEY_HASH)
                        .addAddresses(address)
                        .setAsm("OP_DUP OP_HASH160 abcd OP_EQUALVERIFY OP_CHECKSIG")
                        .setHex("76a914abcd88ac"))
                .setAddress(address)
                .setOpReturnData(ByteString.copyFrom(new byte[]{0x01, 0x02}))
                .setBlockHeight(654321)
                .setRawTxOutput(protobuf.RawTxOutput.newBuilder())
                .build();
    }
}
