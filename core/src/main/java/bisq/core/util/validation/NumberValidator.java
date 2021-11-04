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
import bisq.core.util.ParsingUtils;

/**
 * NumberValidator for validating basic number values.
 * Localisation not supported at the moment
 * The decimal mark can be either "." or ",". Thousand separators are not supported yet,
 * but might be added alter with Local support.
 */
public abstract class NumberValidator extends InputValidator {

    protected String cleanInput(String input) {
        return ParsingUtils.convertCharsForNumber(input);
    }

    protected ValidationResult validateIfNumber(String input) {
        try {
            //noinspection ResultOfMethodCallIgnored
            Double.parseDouble(input);
            return new ValidationResult(true);
        } catch (Exception e) {
            return new ValidationResult(false, Res.get("validation.NaN"));
        }
    }

    protected ValidationResult validateIfNotZero(String input) {
        if (Double.parseDouble(input) == 0)
            return new ValidationResult(false, Res.get("validation.zero"));
        else
            return new ValidationResult(true);
    }

    protected ValidationResult validateIfNotNegative(String input) {
        if (Double.parseDouble(input) < 0)
            return new ValidationResult(false, Res.get("validation.negative"));
        else
            return new ValidationResult(true);
    }
}
