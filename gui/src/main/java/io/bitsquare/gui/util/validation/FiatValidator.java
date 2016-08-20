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

import io.bitsquare.locale.BSResources;

import javax.inject.Inject;

public class FiatValidator extends NumberValidator {
    public static final double MIN_FIAT_VALUE = 0.01;
    public static final double MAX_VALUE = 1000000000000D;

    public double minValue = MIN_FIAT_VALUE;
    public double maxValue = MAX_VALUE;

    @Inject
    public FiatValidator() {
    }

    public void setMinValue(double minValue) {
        this.minValue = minValue;
    }

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid) {
            input = cleanInput(input);
            result = validateIfNumber(input);
        }

        if (result.isValid) {
            result = validateIfNotZero(input)
                    .and(validateIfNotNegative(input))
                    .and(validateIfNotExceedsMinFiatValue(input))
                    .and(validateIfNotExceedsMaxFiatValue(input));
        }

        return result;
    }

    protected ValidationResult validateIfNotExceedsMinFiatValue(String input) {
        double d = Double.parseDouble(input);
        if (d < minValue)
            return new ValidationResult(false, BSResources.get("validation.fiat.toSmall"));
        else
            return new ValidationResult(true);
    }

    protected ValidationResult validateIfNotExceedsMaxFiatValue(String input) {
        double d = Double.parseDouble(input);
        if (d > maxValue)
            return new ValidationResult(false, BSResources.get("validation.fiat.toLarge"));
        else
            return new ValidationResult(true);
    }
}
