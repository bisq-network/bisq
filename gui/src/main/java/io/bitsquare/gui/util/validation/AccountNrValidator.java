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

public final class AccountNrValidator extends BankValidator {

    @Override
    public ValidationResult validate(String input) {
        String message;
        switch (countryCode) {
            case "GB":
                if (isNumberWithFixedLength(input, 8))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.accountNr", 8));
            case "US":
                if (isNumberInRange(input, 4, 17))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.accountNr", "4 - 17"));
            case "BR":
                if (isStringInRange(input, 1, 20))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.accountNrChars", "1 - 20"));
            default:
                return super.validate(input);
        }

    }
}
