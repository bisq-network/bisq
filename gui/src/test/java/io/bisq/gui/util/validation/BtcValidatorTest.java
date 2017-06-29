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

package io.bisq.gui.util.validation;

import io.bisq.gui.util.BSFormatter;
import org.bitcoinj.core.Coin;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BtcValidatorTest {
    @Test
    public void testIsValid() {
        BtcValidator validator = new BtcValidator(new BSFormatter());

        assertTrue(validator.validate("1").isValid);
        assertTrue(validator.validate("0,1").isValid);
        assertTrue(validator.validate("0.1").isValid);
        assertTrue(validator.validate(",1").isValid);
        assertTrue(validator.validate(".1").isValid);
        assertTrue(validator.validate("0.12345678").isValid);
        assertTrue(validator.validate(Coin.SATOSHI.toPlainString()).isValid);

        assertFalse(validator.validate(null).isValid);
        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate("0").isValid);
        assertFalse(validator.validate("0.0").isValid);
        assertFalse(validator.validate("0,1,1").isValid);
        assertFalse(validator.validate("0.1.1").isValid);
        assertFalse(validator.validate("0,000.1").isValid);
        assertFalse(validator.validate("0.000,1").isValid);
        assertFalse(validator.validate("0.123456789").isValid);
        assertFalse(validator.validate("-1").isValid);
        // assertFalse(validator.validate(NetworkParameters.MAX_MONEY.toPlainString()).isValid);
    }

}
