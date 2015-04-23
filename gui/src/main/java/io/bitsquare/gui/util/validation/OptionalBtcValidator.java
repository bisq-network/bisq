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

/**
 * That validator accepts empty inputs
 */
public class OptionalBtcValidator extends BtcValidator {

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = validateIfNotEmpty(input);

        // we accept empty input
        if (!result.isValid)
            return new ValidationResult(true);

        if (result.isValid) {
            input = cleanInput(input);
            result = validateIfNumber(input);
        }

        if (result.isValid) {
            result = validateIfNotZero(input);
            if (result.isValid) {
                result = validateIfNotNegative(input)
                        .and(validateIfNotFractionalBtcValue(input))
                        .and(validateIfNotExceedsMaxBtcValue(input));
            }
            else {
                // we accept zero input
                return new ValidationResult(true);
            }
        }

        return result;
    }
}
