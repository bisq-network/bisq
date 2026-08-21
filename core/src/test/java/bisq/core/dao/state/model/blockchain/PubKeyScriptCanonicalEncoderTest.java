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

import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PubKeyScriptCanonicalEncoderTest {
    @Test
    public void encodeCanonicalMatchesProtobuf() {
        protobuf.PubKeyScript proto = protobuf.PubKeyScript.newBuilder()
                .setReqSigs(2)
                .setScriptType(protobuf.ScriptType.MULTISIG)
                .addAddresses("B1111111111111111111111111111111111")
                .addAddresses("")
                .addAddresses("B2222222222222222222222222222222222")
                .setAsm("2 key1 key2 2 OP_CHECKMULTISIG")
                .setHex("522102aaaa2102bbbb52ae")
                .build();
        PubKeyScript pubKeyScript = PubKeyScript.fromProto(proto);

        assertArrayEquals(pubKeyScript.toProtoMessage().toByteArray(),
                pubKeyScript.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void schemaLocksFieldOrderTypesAndRules() {
        assertEquals(CanonicalSchema.CURRENT_VERSION, PubKeyScript.SCHEMA.getVersion());
        assertEquals(List.of(1, 2, 3, 4, 5), fieldNumbers(PubKeyScript.SCHEMA));
        assertEquals(List.of(
                        CanonicalSchema.FieldType.INT32,
                        CanonicalSchema.FieldType.ENUM,
                        CanonicalSchema.FieldType.REPEATED_STRING,
                        CanonicalSchema.FieldType.STRING,
                        CanonicalSchema.FieldType.STRING),
                fieldTypes(PubKeyScript.SCHEMA));
        assertEquals(List.of(
                        CanonicalSchema.Rule.OMIT_DEFAULT,
                        CanonicalSchema.Rule.OMIT_DEFAULT,
                        CanonicalSchema.Rule.LIST_ORDER,
                        CanonicalSchema.Rule.OMIT_EMPTY,
                        CanonicalSchema.Rule.OMIT_EMPTY),
                fieldRules(PubKeyScript.SCHEMA));
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
