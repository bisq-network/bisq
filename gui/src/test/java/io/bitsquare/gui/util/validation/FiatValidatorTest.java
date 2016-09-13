/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util.validation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FiatValidatorTest {
    @Test
    public void testValidate() {
        FiatValidator validator = new FiatValidator();
        NumberValidator.ValidationResult validationResult;


        assertTrue(validator.validate("1").isValid);
        assertTrue(validator.validate("1,1").isValid);
        assertTrue(validator.validate("1.1").isValid);
        assertTrue(validator.validate(",1").isValid);
        assertTrue(validator.validate(".1").isValid);
        assertTrue(validator.validate("0.01").isValid);
        assertTrue(validator.validate("1000000.00").isValid);
        assertTrue(validator.validate(String.valueOf(FiatValidator.MIN_FIAT_VALUE)).isValid);
        assertTrue(validator.validate(String.valueOf(FiatValidator.MAX_VALUE)).isValid);

        assertFalse(validator.validate(null).isValid);
        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate("a").isValid);
        assertFalse(validator.validate("2a").isValid);
        assertFalse(validator.validate("a2").isValid);
        assertFalse(validator.validate("0").isValid);
        assertFalse(validator.validate("-1").isValid);
        assertFalse(validator.validate("0.0").isValid);
        assertFalse(validator.validate("0,1,1").isValid);
        assertFalse(validator.validate("0.1.1").isValid);
        assertFalse(validator.validate("1,000.1").isValid);
        assertFalse(validator.validate("1.000,1").isValid);
        assertFalse(validator.validate("0.009").isValid);
        assertFalse(validator.validate(String.valueOf(FiatValidator.MIN_FIAT_VALUE - 1)).isValid);
        assertFalse(validator.validate(String.valueOf(FiatValidator.MAX_VALUE + 1)).isValid);
        assertFalse(validator.validate(String.valueOf(Double.MIN_VALUE)).isValid);
        assertFalse(validator.validate(String.valueOf(Double.MAX_VALUE)).isValid);

    }
}
