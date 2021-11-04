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
import bisq.core.util.validation.NumberValidator;

import lombok.Setter;

import javax.annotation.Nullable;

public class PercentageNumberValidator extends NumberValidator {
    @Nullable
    @Setter
    protected Double maxValue; // Keep it Double as we check for null

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid) {
            input = input.replace("%", "");
            input = cleanInput(input);
            result = validateIfNumber(input);
        }
        return result.and(validateIfNotExceedsMaxValue(input));
    }

    private ValidationResult validateIfNotExceedsMaxValue(String input) {
        try {
            double value = Double.parseDouble(input);
            if (maxValue != null && value > maxValue)
                return new ValidationResult(false, Res.get("validation.inputTooLarge", maxValue));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }
}
