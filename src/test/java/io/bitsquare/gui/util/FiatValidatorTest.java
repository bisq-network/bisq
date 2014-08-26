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

package io.bitsquare.gui.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FiatValidatorTest {
    @Test
    public void testValidate() {
        FiatValidator validator = new FiatValidator();
        NumberValidator.ValidationResult validationResult;


        // invalid cases
        validationResult = validator.validate(null);
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("0");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("-1");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("a");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("2a");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("a2");
        assertFalse(validationResult.isValid);

        // at the moment we dont support thousand separators, can be added later
        validationResult = validator.validate("1,100.1");
        assertFalse(validationResult.isValid);

        // at the moment we dont support thousand separators, can be added later
        validationResult = validator.validate("1.100,1");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("1.100.1");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("1,100,1");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(FiatValidator.MIN_FIAT_VALUE - 0.0000001));
        assertFalse(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(FiatValidator.MAX_FIAT_VALUE + 0.0000001));
        assertFalse(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(Double.MIN_VALUE));
        assertFalse(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(Double.MAX_VALUE));
        assertFalse(validationResult.isValid);


        // valid cases
        validationResult = validator.validate("1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate("0,1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate("0.1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate(",1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate(".1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(FiatValidator.MIN_FIAT_VALUE));
        assertTrue(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(FiatValidator.MAX_FIAT_VALUE));
        assertTrue(validationResult.isValid);
    }
}
