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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common;

import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalEncoder;

import com.google.protobuf.Message;
import com.google.protobuf.StringValue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ProtoTest {
    @Test
    @SuppressWarnings("deprecation")
    public void serializeForHashUsesProtobufSerialization() {
        CanonicalPayload payload = new CanonicalPayload();

        assertArrayEquals(payload.serialize(), payload.serializeForHash());
    }

    private static final class CanonicalPayload implements Payload, Canonical {
        @Override
        public Message toProtoMessage() {
            return StringValue.of("protobuf-bytes");
        }

        @Override
        public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
            return new byte[]{0x01, 0x02, 0x03};
        }
    }
}
