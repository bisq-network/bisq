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

import bisq.core.locale.BankUtil;
import bisq.core.locale.Res;

public final class BranchIdValidator extends BankValidator {

    public BranchIdValidator(String countryCode) {
        super(countryCode);
    }

    @Override
    public ValidationResult validate(String input) {
        int length;
        switch (countryCode) {
            case "GB":
                length = 6;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.sortCodeNumber", getLabel(), length));
            case "US":
                length = 9;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.sortCodeNumber", getLabel(), length));
            case "BR":
                if (isStringInRange(input, 2, 6))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.sortCodeChars", getLabel(), "2 - 6"));
            case "AU":
                length = 6;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.sortCodeChars", getLabel(), length));
            case "CA":
                length = 5;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.sortCodeNumber", getLabel(), length));
            case "AR":
                length = 4;
                if(isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.sortCodeNumber", getLabel(), length));
            default:
                return super.validate(input);
        }

    }

    private String getLabel() {
        return BankUtil.getBranchIdLabel(countryCode);
    }

}
