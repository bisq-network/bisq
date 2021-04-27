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
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.validation.InputValidator;

import javax.inject.Inject;

public final class BsqAddressValidator extends InputValidator {

    private final BsqFormatter bsqFormatter;

    @Inject
    public BsqAddressValidator(BsqFormatter bsqFormatter) {
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public ValidationResult validate(String input) {

        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid)
            return validateBsqAddress(input);
        else
            return result;
    }

    private ValidationResult validateBsqAddress(String input) {
        try {
            bsqFormatter.getAddressFromBsqAddress(input);
            return new ValidationResult(true);
        } catch (RuntimeException e) {
            return new ValidationResult(false, Res.get("validation.bsq.invalidFormat"));
        }
    }
}
