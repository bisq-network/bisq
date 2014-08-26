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

import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.NumberValidator;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.NetworkParameters;

import org.junit.Test;

import static org.junit.Assert.*;

public class BtcValidatorTest {
    @Test
    public void testValidate() {
        BtcValidator validator = new BtcValidator();
        NumberValidator.ValidationResult validationResult;

        // invalid cases
        validationResult = validator.validate("0.000000011");// minBtc is "0.00000001"
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("21000001"); //maxBtc is "21000000"
        assertFalse(validationResult.isValid);

        // valid cases
        String minBtc = Coin.SATOSHI.toPlainString(); // "0.00000001"
        validationResult = validator.validate(minBtc);
        assertTrue(validationResult.isValid);

        String maxBtc = Coin.valueOf(NetworkParameters.MAX_MONEY.longValue()).toPlainString(); //"21000000"
        validationResult = validator.validate(maxBtc);
        assertTrue(validationResult.isValid);
    }

}
