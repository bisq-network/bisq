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
import io.bitsquare.messages.locale.BankUtil;

public final class BranchIdValidator extends BankValidator {

    public BranchIdValidator(String countryCode) {
        super(countryCode);
    }

    @Override
    public ValidationResult validate(String input) {
        int length;
        String label = BankUtil.getBankIdLabel(countryCode);
        switch (countryCode) {
            case "GB":
                length = 6;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeNumber", getLabel(), length));
            case "US":
                length = 9;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeNumber", getLabel(), length));
            case "BR":
                if (isStringInRange(input, 2, 6))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeChars", getLabel(), "2 - 6"));
            case "AU":
                length = 6;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeChars", getLabel(), length));
            case "CA":
                length = 5;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeNumber", getLabel(), length));
            default:
                return super.validate(input);
        }

    }

    private String getLabel() {
        String label = BankUtil.getBranchIdLabel(countryCode);
        return label.substring(0, label.length() - 1);
    }

}
