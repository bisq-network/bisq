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

public final class SbpValidator extends PhoneNumberValidator {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Public no-arg constructor required by Guice injector.
    // Superclass' isoCountryCode must be set before validation.
    public SbpValidator() { super("RU"); }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ValidationResult validate(String input) {
        // Some banks allow registering the service with a foreign mobile number,
        // so we accept any number with an explicit non-Russian dialing code and
        // validate it as an international number. Zone 7 is exclusive to Russia
        // and Kazakhstan, so numbers whose digits start with 7, as well as
        // numbers without a dialing code, are validated against the Russian
        // rules as before.
        if (input != null) {
            String trimmedInput = input.trim();
            // A "+" is only meaningful as the single leading character introducing the
            // dialing code. A second or misplaced "+" must not evade either the Russian
            // or the international rules (the base validator strips it silently).
            if (trimmedInput.indexOf('+', 1) >= 0) {
                return new ValidationResult(false,
                        Res.get("validation.phone.invalidInternationalFormat", trimmedInput));
            }
            String pureNumber = trimmedInput.replaceAll("[^A-Za-z0-9]", "");
            if (trimmedInput.startsWith("+") && !pureNumber.startsWith("7")) {
                return validateInternationalNumber(trimmedInput, pureNumber);
            }
        }
        return super.validate(input);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ValidationResult validateInternationalNumber(String input, String pureNumber) {
        // The digits of an E.164 number start with the dialing code, which cannot
        // start with 0. Formatting between the + and the digits (spaces, parens) is
        // accepted, as it already is for Russian numbers.
        if (pureNumber.isEmpty() || pureNumber.charAt(0) == '0') {
            return new ValidationResult(false, Res.get("validation.phone.invalidInternationalFormat", input));
        }
        // Only digits and common number formatting are allowed around the dialing code;
        // anything else must not be silently stripped into a valid-looking number.
        if (!input.matches("\\+[0-9 ().-]*")) {
            return new ValidationResult(false, Res.get("validation.phone.invalidCharacters", input));
        }
        // ITU-T E.164 numbers have at most 15 digits including the dialing
        // code; the shortest assigned international numbers have 7 digits
        if (pureNumber.length() < 7) {
            return new ValidationResult(false, Res.get("validation.phone.insufficientDigits", input));
        }
        if (pureNumber.length() > 15) {
            return new ValidationResult(false, Res.get("validation.phone.tooManyDigits", input));
        }
        return new ValidationResult(true);
    }
}
