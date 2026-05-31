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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CanonicalSchemaTest {
    @Test
    public void newBuilderUsesCurrentVersionByDefault() {
        CanonicalSchema<Object> schema = CanonicalSchema.<Object>newBuilder("TestSchema")
                .int32(1, "value", ignored -> 1)
                .build();

        assertEquals(CanonicalSchema.CURRENT_VERSION, schema.getVersion());
    }

    @Test
    public void newBuilderAcceptsExplicitVersion() {
        CanonicalSchema<Object> schema = CanonicalSchema.<Object>newBuilder("TestSchema", 2)
                .int32(1, "value", ignored -> 1)
                .build();

        assertEquals(2, schema.getVersion());
    }

    @Test
    public void versionMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.newBuilder("TestSchema", 0));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.newBuilder("TestSchema").version(-1));
    }

    @Test
    public void fieldNumberMustBeInProtobufRange() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.<Object>newBuilder("TestSchema")
                        .int32(0, "value", ignored -> 1));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.<Object>newBuilder("TestSchema")
                        .int32(536870912, "value", ignored -> 1));
    }

    @Test
    public void fieldNumberMustNotUseProtobufReservedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.<Object>newBuilder("TestSchema")
                        .int32(19000, "value", ignored -> 1));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalSchema.<Object>newBuilder("TestSchema")
                        .int32(19999, "value", ignored -> 1));
    }

    @Test
    public void fieldNumberAllowsProtobufBoundaryValues() {
        CanonicalSchema.<Object>newBuilder("TestSchema")
                .int32(1, "first", ignored -> 1)
                .int32(18999, "before_reserved", ignored -> 1)
                .int32(20000, "after_reserved", ignored -> 1)
                .int32(536870911, "last", ignored -> 1)
                .build();
    }
}
