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

import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;

import javax.inject.Inject;

public class SecurityDepositValidator extends NumberValidator {

    private final BSFormatter formatter;

    @Inject
    public SecurityDepositValidator(BSFormatter formatter) {
        this.formatter = formatter;
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
                    .and(validateIfNotTooLowPercentageValue(input))
                    .and(validateIfNotTooHighPercentageValue(input));
        }
        return result;
    }


    private ValidationResult validateIfNotTooLowPercentageValue(String input) {
        try {
            double percentage = formatter.parsePercentStringToDouble(input);
            double minPercentage = Restrictions.getMinBuyerSecurityDepositAsPercent();
            if (percentage < minPercentage)
                return new ValidationResult(false,
                        Res.get("validation.inputTooSmall", formatter.formatToPercentWithSymbol(minPercentage)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    private ValidationResult validateIfNotTooHighPercentageValue(String input) {
        try {
            double percentage = formatter.parsePercentStringToDouble(input);
            double maxPercentage = Restrictions.getMaxBuyerSecurityDepositAsPercent();
            if (percentage > maxPercentage)
                return new ValidationResult(false,
                        Res.get("validation.inputTooLarge", formatter.formatToPercentWithSymbol(maxPercentage)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }
}
