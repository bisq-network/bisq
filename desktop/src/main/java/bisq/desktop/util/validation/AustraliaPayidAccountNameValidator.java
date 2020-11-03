
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

import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.RegexValidator;

import javax.inject.Inject;

public final class AustraliaPayidAccountNameValidator extends InputValidator {
    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = super.validate(input);

        if (result.isValid)
            result = lengthValidator.validate(input);
        if (result.isValid)
            result = regexValidator.validate(input);

        return result;
    }

    private final LengthValidator lengthValidator;
    private final RegexValidator regexValidator;

    @Inject
    public AustraliaPayidAccountNameValidator(LengthValidator lengthValidator, RegexValidator regexValidator) {

        lengthValidator.setMinLength(1);
        lengthValidator.setMaxLength(40);
        this.lengthValidator = lengthValidator;

        this.regexValidator = regexValidator;
    }
}
