/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.util.validation;

import io.bisq.gui.util.BSFormatter;

import javax.inject.Inject;

/**
 * That validator accepts empty inputs
 */
public class OptionalBtcValidator extends BtcValidator {

    @Inject
    public OptionalBtcValidator(BSFormatter formatter) {
        super(formatter);
    }

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
            } else {
                // we accept zero input
                return new ValidationResult(true);
            }
        }

        return result;
    }
}
