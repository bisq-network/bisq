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

package bisq.desktop.util.validation;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.util.validation.RegexValidator;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegexValidatorTest {

    @Before
    public void setup() {
        final BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        final String currencyCode = baseCurrencyNetwork.getCurrencyCode();
        Res.setBaseCurrencyCode(currencyCode);
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
        CurrencyUtil.setBaseCurrencyCode(currencyCode);
    }

    @Test
    public void validate() throws Exception {
        RegexValidator validator = new RegexValidator();

        assertTrue(validator.validate("").isValid);
        assertTrue(validator.validate(null).isValid);
        assertTrue(validator.validate("123456789").isValid);

        validator.setPattern("[a-z]*");

        assertTrue(validator.validate("abcdefghijklmnopqrstuvwxyz").isValid);
        assertTrue(validator.validate("").isValid);
        assertTrue(validator.validate(null).isValid);

        assertFalse(validator.validate("123").isValid); // invalid
        assertFalse(validator.validate("ABC").isValid); // invalid

        validator.setPattern("[a-z]+");

        assertTrue(validator.validate("abcdefghijklmnopqrstuvwxyz").isValid);

        assertFalse(validator.validate("123").isValid); // invalid
        assertFalse(validator.validate("ABC").isValid); // invalid
        assertFalse(validator.validate("").isValid); // invalid
        assertFalse(validator.validate(null).isValid); // invalid

    }

}
