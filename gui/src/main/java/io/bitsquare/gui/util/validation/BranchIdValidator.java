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

public final class BranchIdValidator extends BankValidator {
    @Override
    public ValidationResult validate(String input) {
        int length;
        switch (countryCode) {
            case "GB":
                length = 6;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeNumber", "Sort code", length));
            case "US":
                length = 9;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeNumber", "Routing number", length));
            case "BR":
                if (isStringInRange(input, 2, 6))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeChars", "Branch code", "2 - 6"));
            default:
                return super.validate(input);
        }

    }


}
