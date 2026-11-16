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

public class SolanaAddressValidatorTest {
    private final AddressValidator validator = new SolanaAddressValidator();

    @Test
    public void testValidAddresses() {
        assertTrue(validator.validate("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB").isValid());
        assertTrue(validator.validate("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v").isValid());
        assertTrue(validator.validate("2b1kV6DkPAnxd5ixfnxCpjxmKwqjjaYmCZfHsFu24GXo").isValid());
        // 32 zero bytes
        assertTrue(validator.validate("11111111111111111111111111111111").isValid());
    }

    @Test
    public void testInvalidAddresses() {
        assertFalse(validator.validate("").isValid());
        // 31 and 33 bytes
        assertFalse(validator.validate("tVojvhToWjQ8Xvo4UPx2Xz9eRy7auyYMmZBjc2XfN").isValid());
        assertFalse(validator.validate("JJEfe6DcPM2ziB2vfUWDV6aHVerXRGkv3TcyvJUNGHZz").isValid());
        // Not a Base58 character
        assertFalse(validator.validate("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNY0").isValid());
        // Tron and Bitcoin addresses decode to 25 bytes
        assertFalse(validator.validate("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t").isValid());
        assertFalse(validator.validate("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa").isValid());
        // Ethereum address
        assertFalse(validator.validate("0xdAC17F958D2ee523a2206206994597C13D831ec7").isValid());
    }
}
