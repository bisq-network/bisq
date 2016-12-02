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

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class AltCoinAddressValidatorTest {

    @Test
    public void testBTC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("BTC");

        assertTrue(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem").isValid);
        assertTrue(validator.validate("3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX").isValid);
        assertTrue(validator.validate("1111111111111111111114oLvT2").isValid);
        assertTrue(validator.validate("1BitcoinEaterAddressDontSendf59kuE").isValid);

        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#").isValid);
    }

}
