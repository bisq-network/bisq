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

package bisq.core.alert;

import bisq.common.crypto.Sig;
import bisq.common.encoding.canonical.CanonicalEncoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class AlertCanonicalEncoderTest {
    @Test
    void serializeForHashMatchesProtobufForCanonicalSchema() {
        Alert alert = new Alert("Mandatory update is available",
                true,
                true,
                "1.9.99",
                Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic()),
                "signature-base64");

        assertArrayEquals(alert.serialize(), alert.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(alert.encodeCanonical(CanonicalEncoder.DEFAULT), alert.serializeForHash());
    }
}
