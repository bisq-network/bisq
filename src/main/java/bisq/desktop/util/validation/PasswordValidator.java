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

import bisq.common.locale.Res;

public final class PasswordValidator extends InputValidator {

    private ValidationResult externalValidationResult;

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid)
            result = validateMinLength(input);

        if (externalValidationResult != null && !externalValidationResult.isValid)
            return externalValidationResult;

        return result;
    }

    public void setExternalValidationResult(ValidationResult externalValidationResult) {
        this.externalValidationResult = externalValidationResult;
    }

    private ValidationResult validateMinLength(String input) {
        if (input.length() < 8)
            return new ValidationResult(false, Res.get("validation.passwordTooShort"));
        else if (input.length() > 50)
            return new ValidationResult(false, Res.get("validation.passwordTooLong"));
        else
            return new ValidationResult(true);
    }

}
