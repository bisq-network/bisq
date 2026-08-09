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

public class SbpValidatorTest {
    private SbpValidator validator;

    @BeforeEach
    void setup() {
        Res.setup();
        validator = new SbpValidator();
    }

    @Test
    void acceptsRussianNumbers() {
        assertTrue(validator.validate("+7 903 123 45 67").isValid);
        assertTrue(validator.validate("9031234567").isValid);
    }

    @Test
    void rejectsInvalidRussianNumbers() {
        assertFalse(validator.validate("903123").isValid);
        assertFalse(validator.validate("+7 903 123 45").isValid);
        assertFalse(validator.validate("1234567").isValid);
    }

    @Test
    void acceptsForeignNumbersInInternationalFormat() {
        assertTrue(validator.validate("+49 12345").isValid); // 7 digits
        assertTrue(validator.validate("+49 1234567890123").isValid); // 15 digits
        assertTrue(validator.validate("+49 151 12345678").isValid);
        assertTrue(validator.validate("+380 50 123 45 67").isValid);
        assertTrue(validator.validate("+1 415 555 0132").isValid);
        // formatting between the + and the digits is accepted, as for Russian numbers
        assertTrue(validator.validate("+ 49 151 12345678").isValid);
        assertTrue(validator.validate("+(49) 151 12345678").isValid);
    }

    @Test
    void rejectsInvalidForeignNumbers() {
        assertFalse(validator.validate("+49 1234").isValid); // 6 digits
        assertFalse(validator.validate("+49 12345678901234").isValid); // 16 digits
        assertFalse(validator.validate("+49 12AB34").isValid);
        assertFalse(validator.validate("+49 123").isValid);
        assertFalse(validator.validate("+49 1234567890123456").isValid);
        // a + is only valid as the single leading character
        assertFalse(validator.validate("+4+9 151 12345678").isValid);
        assertFalse(validator.validate("+49 151 12345678+").isValid);
        // unsupported punctuation must not be silently stripped into a valid number
        assertFalse(validator.validate("+49@151 12345678").isValid);
        assertFalse(validator.validate("+49/151 12345678").isValid);
    }

    @Test
    void rejectsEmptyInput() {
        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate(null).isValid);
    }

    @Test
    void rejectsMalformedRussianNumbers() {
        // a second + must not evade the Russian rules even when the digits are a
        // valid Russian number; "+ 7 12345678" fails the length rule and a bare
        // "+" fails the international format check
        assertFalse(validator.validate("++7 903 123 45 67").isValid);
        assertFalse(validator.validate("++7 12345678").isValid);
        assertFalse(validator.validate("+ 7 12345678").isValid);
        assertFalse(validator.validate("+").isValid);
    }

    @Test
    void rejectsMalformedInternationalPrefixes() {
        // exactly one leading + and a dialing code not starting with 0
        assertFalse(validator.validate("++49 151 12345678").isValid);
        assertFalse(validator.validate("+049 151 12345678").isValid);
    }

    @Test
    void acceptsKazakhNumbersUnderRussianRules() {
        // zone 7 is shared with Kazakhstan and has the same 10 digit national length
        assertTrue(validator.validate("+7 701 123 45 67").isValid);
    }

}
