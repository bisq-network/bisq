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

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class IntegerValidator extends InputValidator {
    private int minValue = Integer.MIN_VALUE;
    private int maxValue = Integer.MAX_VALUE;
    private int intValue;

    public IntegerValidator() {
    }

    public IntegerValidator(int minValue, int maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public ValidationResult validate(String input) {
        ValidationResult validationResult = super.validate(input);
        if (!validationResult.isValid)
            return validationResult;

        if (!isInteger(input))
            return new ValidationResult(false, Res.get("validation.notAnInteger"));

        if (isBelowMinValue(intValue))
            return new ValidationResult(false, Res.get("validation.btc.toSmall", minValue));

        if (isAboveMaxValue(intValue))
            return new ValidationResult(false, Res.get("validation.btc.toLarge", maxValue));

        return validationResult;
    }

    private boolean isBelowMinValue(int intValue) {
        return intValue < minValue;
    }

    private boolean isAboveMaxValue(int intValue) {
        return intValue > maxValue;
    }

    private boolean isInteger(String input) {
        try {
            intValue = Integer.parseInt(input);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
