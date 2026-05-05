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

package bisq.core.util;

import org.bitcoinj.core.ECKey;

import org.bouncycastle.util.encoders.Hex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ValidatorTest {
    private static final String FIELD_NAME = "multiSigPubKey";

    @Test
    public void checkCompressedSecp256k1PubKeyAcceptsValidCompressedKey() {
        byte[] pubKey = new ECKey().getPubKeyPoint().getEncoded(true);

        assertEquals(33, pubKey.length);
        assertSame(pubKey, Validator.checkCompressedSecp256k1PubKey(pubKey, FIELD_NAME));
    }

    @Test
    public void checkCompressedSecp256k1PubKeyRejectsNullKey() {
        assertThrows(NullPointerException.class,
                () -> Validator.checkCompressedSecp256k1PubKey(null, FIELD_NAME));
    }

    @Test
    public void checkCompressedSecp256k1PubKeyRejectsEmptyKey() {
        assertThrows(IllegalArgumentException.class,
                () -> Validator.checkCompressedSecp256k1PubKey(new byte[0], FIELD_NAME));
    }

    @Test
    public void checkCompressedSecp256k1PubKeyRejectsUncompressedKey() {
        byte[] pubKey = new ECKey().getPubKeyPoint().getEncoded(false);

        assertEquals(65, pubKey.length);
        assertThrows(IllegalArgumentException.class,
                () -> Validator.checkCompressedSecp256k1PubKey(pubKey, FIELD_NAME));
    }

    @Test
    public void checkCompressedSecp256k1PubKeyRejectsMalformedCompressedKey() {
        assertThrows(IllegalArgumentException.class,
                () -> Validator.checkCompressedSecp256k1PubKey(new byte[33], FIELD_NAME));
    }

    @Test
    public void checkCompressedSecp256k1PubKeyRejectsStructurallyValidCompressedKeyWithInvalidCurvePoint() {
        byte[] pubKey = new byte[33];
        pubKey[0] = 0x02;

        assertEquals(33, pubKey.length);
        assertThrows(IllegalArgumentException.class,
                () -> Validator.checkCompressedSecp256k1PubKey(pubKey, FIELD_NAME));
    }

    @Test
    public void checkCompressedSecp256k1PubKeyAcceptsValidCompressedCurvePoints() {
        String[] validEncodings = {
                "020000000000000000000000000000000000000000000000000000000000000001",
                "020000000000000000000000000000000000000000000000000000000000000002",
                "020000000000000000000000000000000000000000000000000000000000000003",
                "020000000000000000000000000000000000000000000000000000000000000004",
                "020000000000000000000000000000000000000000000000000000000000000006",
                "020000000000000000000000000000000000000000000000000000000000000008",
                "02000000000000000000000000000000000000000000000000000000000000000c",
                "02000000000000000000000000000000000000000000000000000000000000000d",
                "02000000000000000000000000000000000000000000000000000000000000000e"
        };

        for (String validEncoding : validEncodings) {
            byte[] pubKey = Hex.decode(validEncoding);
            assertDoesNotThrow(() -> Validator.checkCompressedSecp256k1PubKey(pubKey, FIELD_NAME),
                    validEncoding);
        }
    }

    @Test
    public void checkCompressedSecp256k1PubKeyRejectsInvalidCompressedCurvePoints() {
        String[] invalidEncodings = {
                "020000000000000000000000000000000000000000000000000000000000000000",
                "020000000000000000000000000000000000000000000000000000000000000005",
                "020000000000000000000000000000000000000000000000000000000000000007",
                "020000000000000000000000000000000000000000000000000000000000000009",
                "02000000000000000000000000000000000000000000000000000000000000000a",
                "02000000000000000000000000000000000000000000000000000000000000000b",
                "02000000000000000000000000000000000000000000000000000000000000000f"
        };

        for (String invalidEncoding : invalidEncodings) {
            byte[] pubKey = Hex.decode(invalidEncoding);
            assertThrows(IllegalArgumentException.class,
                    () -> Validator.checkCompressedSecp256k1PubKey(pubKey, FIELD_NAME),
                    invalidEncoding);
        }
    }
}
