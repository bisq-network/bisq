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

public class BitSquareNumberValidatorTest
{
    @Test
    public void testValidateStringAsDouble()
    {
        assertTrue(BitSquareValidator.validateStringAsDouble("0"));
        assertTrue(BitSquareValidator.validateStringAsDouble("1"));
        assertTrue(BitSquareValidator.validateStringAsDouble("0,1"));
        assertTrue(BitSquareValidator.validateStringAsDouble("0.01"));

        assertFalse(BitSquareValidator.validateStringAsDouble(""));
        assertFalse(BitSquareValidator.validateStringAsDouble("a"));
        assertFalse(BitSquareValidator.validateStringAsDouble("0.0.1"));
        assertFalse(BitSquareValidator.validateStringAsDouble("1,000.1"));
        assertFalse(BitSquareValidator.validateStringAsDouble("1.000,1"));
        assertFalse(BitSquareValidator.validateStringAsDouble(null));
    }

    @Test
    public void testValidateStringNotEmpty()
    {
        assertTrue(BitSquareValidator.validateStringNotEmpty("a"));
        assertTrue(BitSquareValidator.validateStringNotEmpty("123"));

        assertFalse(BitSquareValidator.validateStringNotEmpty(""));
        assertFalse(BitSquareValidator.validateStringNotEmpty(" "));
        assertFalse(BitSquareValidator.validateStringNotEmpty(null));
    }


}
