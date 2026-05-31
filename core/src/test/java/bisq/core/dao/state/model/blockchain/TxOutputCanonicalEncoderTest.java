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

package bisq.core.dao.state.model.blockchain;

import bisq.core.encoding.canonical.CanonicalEncoder;
import bisq.core.encoding.canonical.CanonicalSchema;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TxOutputCanonicalEncoderTest {
    @Test
    public void encodeCanonicalMatchesProtobufWithNestedPubKeyScript() {
        protobuf.BaseTxOutput proto = protobuf.BaseTxOutput.newBuilder()
                .setIndex(2)
                .setValue(1234567890123L)
                .setTxId("8d1b4f7a23")
                .setPubKeyScript(protobuf.PubKeyScript.newBuilder()
                        .setReqSigs(1)
                        .setScriptType(protobuf.ScriptType.PUB_KEY_HASH)
                        .addAddresses("B2N7mPZyBSq7wQ3k3m1TN1tG4WnQnE4VxQ")
                        .setAsm("OP_DUP OP_HASH160 c0ffee OP_EQUALVERIFY OP_CHECKSIG")
                        .setHex("76a914c0ffee88ac"))
                .setAddress("B2N7mPZyBSq7wQ3k3m1TN1tG4WnQnE4VxQ")
                .setOpReturnData(ByteString.copyFrom(new byte[]{0x01, 0x02, 0x03, 0x04}))
                .setBlockHeight(802345)
                .setTxOutput(protobuf.TxOutput.newBuilder()
                        .setTxOutputType(protobuf.TxOutputType.LOCKUP_OUTPUT)
                        .setLockTime(-1)
                        .setUnlockBlockHeight(812345))
                .build();
        TxOutput txOutput = TxOutput.fromProto(proto);

        assertArrayEquals(txOutput.toProtoMessage().toByteArray(),
                txOutput.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void encodeCanonicalMatchesProtobufWithoutNullableFields() {
        protobuf.BaseTxOutput proto = protobuf.BaseTxOutput.newBuilder()
                .setIndex(0)
                .setValue(250000000L)
                .setTxId("genesis-output")
                .setBlockHeight(571747)
                .setTxOutput(protobuf.TxOutput.newBuilder()
                        .setTxOutputType(protobuf.TxOutputType.GENESIS_OUTPUT)
                        .setLockTime(-1))
                .build();
        TxOutput txOutput = TxOutput.fromProto(proto);

        assertArrayEquals(txOutput.toProtoMessage().toByteArray(),
                txOutput.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void schemaLocksFieldOrderTypesAndRules() {
        assertEquals("BaseTxOutput", TxOutput.SCHEMA.getMessageName());
        assertEquals(CanonicalSchema.CURRENT_VERSION, TxOutput.SCHEMA.getVersion());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 9), fieldNumbers(TxOutput.SCHEMA));
        assertEquals(List.of(
                        CanonicalSchema.FieldType.INT32,
                        CanonicalSchema.FieldType.INT64,
                        CanonicalSchema.FieldType.STRING,
                        CanonicalSchema.FieldType.COMPOSE,
                        CanonicalSchema.FieldType.STRING,
                        CanonicalSchema.FieldType.BYTES,
                        CanonicalSchema.FieldType.INT32,
                        CanonicalSchema.FieldType.EXTEND),
                fieldTypes(TxOutput.SCHEMA));
        assertEquals(List.of(
                        CanonicalSchema.Rule.OMIT_DEFAULT,
                        CanonicalSchema.Rule.OMIT_DEFAULT,
                        CanonicalSchema.Rule.OMIT_EMPTY,
                        CanonicalSchema.Rule.OMIT_NULL,
                        CanonicalSchema.Rule.OMIT_EMPTY,
                        CanonicalSchema.Rule.OMIT_EMPTY,
                        CanonicalSchema.Rule.OMIT_DEFAULT,
                        CanonicalSchema.Rule.OMIT_NULL),
                fieldRules(TxOutput.SCHEMA));
        assertEquals(PubKeyScript.SCHEMA, TxOutput.SCHEMA.getFields().get(3).getSchema());
        assertEquals("TxOutput", TxOutput.SCHEMA.getFields().get(7).getSchema().getMessageName());
    }

    private static List<Integer> fieldNumbers(CanonicalSchema<?> schema) {
        return schema.getFields().stream()
                .map(CanonicalSchema.Field::getNumber)
                .collect(Collectors.toList());
    }

    private static List<CanonicalSchema.FieldType> fieldTypes(CanonicalSchema<?> schema) {
        return schema.getFields().stream()
                .map(CanonicalSchema.Field::getType)
                .collect(Collectors.toList());
    }

    private static List<CanonicalSchema.Rule> fieldRules(CanonicalSchema<?> schema) {
        return schema.getFields().stream()
                .map(CanonicalSchema.Field::getRule)
                .collect(Collectors.toList());
    }
}
