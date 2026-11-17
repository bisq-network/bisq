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
import bisq.core.payment.CustomPaymentMethodUtil;
import bisq.core.util.validation.InputValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomPaymentMethodNameValidatorTest {
    private CustomPaymentMethodNameValidator validator;

    @BeforeEach
    void setup() {
        Res.setup();
        validator = new CustomPaymentMethodNameValidator();
    }

    @Test
    void acceptsNameOfAPaymentMethodWhichBisqDoesNotCover() {
        assertTrue(validator.validate("M-Pesa").isValid);
        assertTrue(validator.validate("Orange Money (CI)").isValid);
        assertTrue(validator.validate("a".repeat(CustomPaymentMethodUtil.MAX_NAME_LENGTH)).isValid);
    }

    // The account stores the name without the spaces around it and without characters which are not allowed
    @Test
    void acceptsNameWhichIsValidOnceItIsCleaned() {
        assertTrue(validator.validate("  M-Pesa  ").isValid);
        assertTrue(validator.validate(" " + "a".repeat(CustomPaymentMethodUtil.MAX_NAME_LENGTH) + " ").isValid);
        assertTrue(validator.validate("\u200BM-Pesa").isValid);
    }

    @Test
    void rejectsEmptyName() {
        assertEquals(Res.get("validation.empty"), validator.validate(null).errorMessage);
        assertEquals(Res.get("validation.empty"), validator.validate("").errorMessage);
        assertEquals(Res.get("validation.empty"), validator.validate("   ").errorMessage);
    }

    @Test
    void rejectsNameAboveTheMaxLength() {
        InputValidator.ValidationResult result = validator.validate(
                "a".repeat(CustomPaymentMethodUtil.MAX_NAME_LENGTH + 1));

        assertFalse(result.isValid);
        assertEquals("The name must not be longer than 20 characters.", result.errorMessage);
    }

    @Test
    void rejectsNameWithoutLetterOrDigit() {
        InputValidator.ValidationResult result = validator.validate("-+-");

        assertFalse(result.isValid);
        assertEquals("The name must contain a letter or number, and none of its characters may have more than 4 " +
                "accents or other marks.", result.errorMessage);
    }

    // The characters which are not shown are removed before the name is compared with the payment methods of Bisq
    @Test
    void rejectsNameOfAPaymentMethodOfBisqWithCharactersWhichAreNotShown() {
        for (String name : new String[]{"SEPA\uFE0F", "S\u200BEPA", "Zelle\u3164"}) {
            InputValidator.ValidationResult result = validator.validate(name);

            assertFalse(result.isValid, name);
            assertEquals(Res.get("payment.custom.name.isBisqPaymentMethod"), result.errorMessage, name);
        }
    }

    @Test
    void rejectsNameOfAPaymentMethodOfBisq() {
        for (String name : new String[]{"SEPA", "sepa instant", "Zelle", "F2F", "revolut", "Cash by mail", "Custom"}) {
            InputValidator.ValidationResult result = validator.validate(name);

            assertFalse(result.isValid, name);
            assertEquals(Res.get("payment.custom.name.isBisqPaymentMethod"), result.errorMessage, name);
        }
    }

    // Venmo and Cash App were removed for their chargeback risk
    @Test
    void rejectsNameOfARemovedPaymentMethod() {
        assertFalse(validator.validate("Venmo").isValid);
        assertFalse(validator.validate("Cash App").isValid);
        assertFalse(validator.validate("OKPay").isValid);
        assertFalse(validator.validate("Chase QuickPay").isValid);
    }
}
