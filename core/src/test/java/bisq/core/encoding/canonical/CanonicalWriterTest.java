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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class CanonicalWriterTest {
    @Test
    public void writeInt32UsesShortestVarintAtBoundary() {
        CanonicalWriter writer = new CanonicalWriter();

        writer.writeInt32(1, 127);
        writer.writeInt32(2, 128);

        assertArrayEquals(bytes(
                        0x08, 0x7f,
                        0x10, 0x80, 0x01),
                writer.toByteArray());
    }

    @Test
    public void writeInt64UsesShortestVarintAtBoundary() {
        CanonicalWriter writer = new CanonicalWriter();

        writer.writeInt64(1, 127L);
        writer.writeInt64(2, 128L);

        assertArrayEquals(bytes(
                        0x08, 0x7f,
                        0x10, 0x80, 0x01),
                writer.toByteArray());
    }

    @Test
    public void writeInt32NegativeValueUsesTenByteVarint() {
        CanonicalWriter writer = new CanonicalWriter();

        writer.writeInt32(1, -1);

        assertArrayEquals(bytes(
                        0x08,
                        0xff, 0xff, 0xff, 0xff, 0xff,
                        0xff, 0xff, 0xff, 0xff, 0x01),
                writer.toByteArray());
    }

    @Test
    public void writeInt64NegativeValueUsesTenByteVarint() {
        CanonicalWriter writer = new CanonicalWriter();

        writer.writeInt64(1, -1L);

        assertArrayEquals(bytes(
                        0x08,
                        0xff, 0xff, 0xff, 0xff, 0xff,
                        0xff, 0xff, 0xff, 0xff, 0x01),
                writer.toByteArray());
    }

    @Test
    public void writeTagUsesMultiByteVarint() {
        CanonicalWriter writer = new CanonicalWriter();

        writer.writeInt32(16, 1);
        writer.writeString(2048, "x");

        assertArrayEquals(bytes(
                        0x80, 0x01, 0x01,
                        0x82, 0x80, 0x01, 0x01, 0x78),
                writer.toByteArray());
    }

    @Test
    public void writeRepeatedStringPreservesEmptyElements() {
        CanonicalWriter writer = new CanonicalWriter();

        writer.writeRepeatedString(3, List.of("alpha", "", "omega"));

        assertArrayEquals(bytes(
                        0x1a, 0x05, 0x61, 0x6c, 0x70, 0x68, 0x61,
                        0x1a, 0x00,
                        0x1a, 0x05, 0x6f, 0x6d, 0x65, 0x67, 0x61),
                writer.toByteArray());
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
