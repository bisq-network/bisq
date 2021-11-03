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

package bisq.core.util.validation;

import bisq.core.locale.Res;

import javax.inject.Inject;

public abstract class MonetaryValidator extends NumberValidator {

    protected abstract double getMinValue();

    @SuppressWarnings("SameReturnValue")
    protected abstract double getMaxValue();

    @Inject
    public MonetaryValidator() {
    }

    @Override
    public InputValidator.ValidationResult validate(String input) {
        InputValidator.ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid) {
            input = cleanInput(input);
            result = validateIfNumber(input);
        }

        if (result.isValid) {
            result = result.andValidation(input,
                    this::validateIfNotZero,
                    this::validateIfNotNegative,
                    this::validateIfNotExceedsMinValue,
                    this::validateIfNotExceedsMaxValue);
        }

        return result;
    }

    protected InputValidator.ValidationResult validateIfNotExceedsMinValue(String input) {
        double d = Double.parseDouble(input);
        if (d < getMinValue())
            return new InputValidator.ValidationResult(false, Res.get("validation.fiat.toSmall"));
        else
            return new InputValidator.ValidationResult(true);
    }

    protected InputValidator.ValidationResult validateIfNotExceedsMaxValue(String input) {
        double d = Double.parseDouble(input);
        if (d > getMaxValue())
            return new InputValidator.ValidationResult(false, Res.get("validation.fiat.toLarge"));
        else
            return new InputValidator.ValidationResult(true);
    }
}
