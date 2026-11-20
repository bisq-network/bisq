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
package bisq.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TronAddressValidatorTest {
    private final AddressValidator validator = new TronAddressValidator();

    @Test
    public void testValidAddresses() {
        assertTrue(validator.validate("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t").isValid());
        assertTrue(validator.validate("TPFqcBAaaUMCSVRCqPaQ9QnzKhmuoLR6Rc").isValid());
        assertTrue(validator.validate("TFNirp6PbqYE1ZTtWuCMUKJWLNZkoCoeFJ").isValid());
    }

    @Test
    public void testInvalidAddresses() {
        assertFalse(validator.validate("").isValid());
        // Wrong checksum
        assertFalse(validator.validate("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6u").isValid());
        // Truncated
        assertFalse(validator.validate("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6").isValid());
        // Not a Base58 character
        assertFalse(validator.validate("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj0t").isValid());
        // Bitcoin address: valid checksum, version byte 0x00
        assertFalse(validator.validate("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa").isValid());
        // Valid checksum, version byte 0x42
        assertFalse(validator.validate("TpSyGx2w2bR9GdrDrYtrYwbvSPhwSmg7ER").isValid());
        // Valid checksum, version byte 0x41, account hash of 19 bytes
        assertFalse(validator.validate("6z7t3TSyVBs8aaTzWUzfkus6r92s6MS25").isValid());
        // Ethereum and Solana addresses
        assertFalse(validator.validate("0xdAC17F958D2ee523a2206206994597C13D831ec7").isValid());
        assertFalse(validator.validate("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB").isValid());
    }
}
