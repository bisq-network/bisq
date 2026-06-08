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

package bisq.core.encoding.canonical;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CanonicalSchemaTest {
    @Test
    public void newBuilderUsesCurrentVersionByDefault() {
        CanonicalSchema<Object> schema = CanonicalSchema.<Object>newBuilder()
                .int32(1, ignored -> 1)
                .build();

        assertEquals(CanonicalSchema.CURRENT_VERSION, schema.getVersion());
    }

    @Test
    public void newBuilderAcceptsExplicitVersion() {
        CanonicalSchema<Object> schema = CanonicalSchema.<Object>newBuilder(2)
                .int32(1, ignored -> 1)
                .build();

        assertEquals(2, schema.getVersion());
    }

    @Test
    public void versionMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.newBuilder(0));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.newBuilder().version(-1));
    }

    @Test
    public void fieldNumberMustBeInProtobufRange() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.<Object>newBuilder()
                        .int32(0, ignored -> 1));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.<Object>newBuilder()
                        .int32(536870912, ignored -> 1));
    }

    @Test
    public void fieldNumberMustNotUseProtobufReservedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.<Object>newBuilder()
                        .int32(19000, ignored -> 1));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.<Object>newBuilder()
                        .int32(19999, ignored -> 1));
    }

    @Test
    public void fieldNumberAllowsProtobufBoundaryValues() {
        CanonicalSchema.<Object>newBuilder()
                .int32(1, ignored -> 1)
                .int32(18999, ignored -> 1)
                .int32(20000, ignored -> 1)
                .int32(536870911, ignored -> 1)
                .build();
    }

    @Test
    public void mapStringToComposeDeclaresMapFieldAndOrderRule() {
        CanonicalSchema<MapHolder> schema = CanonicalSchema.<MapHolder>newBuilder()
                .mapStringToCompose(1,
                        MapHolder::getValues,
                        CanonicalSchema.<MapValue>newBuilder()
                                .int32(1, MapValue::getValue)
                                .build(),
                        TreeMapIterator.naturalOrder())
                .build();

        CanonicalSchema.Field<MapHolder> field = schema.getFields().get(0);
        assertEquals(CanonicalSchema.FieldType.MAP, field.getType());
        assertEquals(CanonicalSchema.Rule.MAP_ORDER, field.getRule());
        assertNotNull(field.getMapEncoding());
    }

    private static final class MapHolder {
        private Map<String, MapValue> getValues() {
            return Collections.emptyMap();
        }
    }

    private static final class MapValue {
        private int getValue() {
            return 1;
        }
    }
}
