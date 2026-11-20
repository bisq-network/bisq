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
import bisq.core.payment.CustomPaymentMethodUtil;
import bisq.core.util.validation.InputValidator;

/**
 * Validates the name which a user gives to a custom payment method. The name is validated as the account stores
 * it (see {@link CustomPaymentMethodUtil#cleanName(String)}).
 */
public final class CustomPaymentMethodNameValidator extends InputValidator {

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = super.validate(input);
        if (!result.isValid) {
            return result;
        }

        String name = CustomPaymentMethodUtil.cleanName(input);
        if (name.length() > CustomPaymentMethodUtil.MAX_NAME_LENGTH) {
            return new ValidationResult(false,
                    Res.get("payment.custom.name.tooLong", CustomPaymentMethodUtil.MAX_NAME_LENGTH));
        }
        if (!CustomPaymentMethodUtil.isValidName(name)) {
            return new ValidationResult(false,
                    Res.get("payment.custom.name.invalid", CustomPaymentMethodUtil.MAX_MARKS_IN_A_ROW));
        }
        // The custom payment method is for payment methods which Bisq does not cover
        if (CustomPaymentMethodUtil.isNameOfBisqPaymentMethod(name)) {
            return new ValidationResult(false, Res.get("payment.custom.name.isBisqPaymentMethod"));
        }
        return new ValidationResult(true);
    }
}
