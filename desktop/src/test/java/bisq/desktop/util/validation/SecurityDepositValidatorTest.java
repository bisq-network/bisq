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

import bisq.core.locale.Res;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecurityDepositValidatorTest {

    private SecurityDepositValidator validator;

    @BeforeEach
    public void setup() {
        Res.setup();
        validator = new SecurityDepositValidator();
    }

    @Test
    public void testValidateWithDefaultMinimum() {
        assertFalse(validator.validate("14.99").isValid);
        assertTrue(validator.validate("15.00").isValid);
        assertTrue(validator.validate("30.00").isValid);
        assertTrue(validator.validate("50.00").isValid);
        assertFalse(validator.validate("50.01").isValid);

        assertFalse(validator.validate(null).isValid);
        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate("0").isValid);
        assertFalse(validator.validate("-15").isValid);
        assertFalse(validator.validate("abc").isValid);
    }
}
