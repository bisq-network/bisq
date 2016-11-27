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
import io.bitsquare.locale.BankUtil;
import org.apache.commons.lang3.StringUtils;

public final class AccountNrValidator extends BankValidator {
    public AccountNrValidator(String countryCode) {
        super(countryCode);
    }

    @Override
    public ValidationResult validate(String input) {
        int length;
        String input2;
        switch (countryCode) {
            case "GB":
                length = 8;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.accountNr", length));
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
            case "NZ":
                input2 = input != null ? input.replaceAll("-", "") : null;
                if (isNumberInRange(input2, 15, 16))
                    return super.validate(input);
                else
                    return new ValidationResult(false, "Account number must be of format: 03-1587-0050000-00");
            case "AU":
                if (isNumberInRange(input, 4, 10))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.accountNr", "4 - 10"));
            case "CA":
                if (isNumberInRange(input, 7, 12))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.accountNr", "7 - 12"));
            case "MX":
                length = 18;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, BSResources.get("validation.sortCodeNumber", getLabel(), length));
            case "HK":
                input2 = input != null ? input.replaceAll("-", "") : null;
                if (isNumberInRange(input2, 9, 12))
                    return super.validate(input);
                else
                    return new ValidationResult(false, "Account number must be of format: 005-231289-112");
	    case "NO":
		length = 11;
		// 11 digits, last digit is checksum.  Checksum algoritm is 
		// MOD11 with weights 2,3,4,5,6,7,2,3,4,5 right to left.
		// First remove whitespace and periods.  Normal formatting is: 
		// 1234.56.78903
		input2 = StringUtils.remove(input, ' ');
        	input2 = StringUtils.remove(input2, '.');
		// 11 digits, numbers only
		if (input2.length() != length || !StringUtils.isNumeric(input2))
                    return new ValidationResult(false, BSResources.get("validation.sortCodeNumber", getLabel(), length));
		int lastDigit = Character.getNumericValue(input.charAt(input2.length() - 1));
		if (getMod11ControlDigit(input2) != lastDigit)
                    return new ValidationResult(false, "Kontonummer har feil sjekksum");
		return super.validate(input);

            default:
                return super.validate(input);
        }
    }


    private String getLabel() {
        String label = BankUtil.getAccountNrLabel(countryCode);
        return label.substring(0, label.length() - 1);
    }


    private static int getMod11ControlDigit(String kontonr) {
	int sumForMod = 0;
	int controlNumber = 2;
	char[] knr = kontonr.toCharArray();

	for(int i = knr.length-2; i >= 0; i--) {
	    sumForMod += (Character.getNumericValue(knr[i]) * controlNumber);
	    controlNumber++;

	    if(controlNumber > 7) {
		controlNumber = 2;
	    }
	}
	int calculus = (11 - sumForMod%11);
	if(calculus == 11) {
	    return 0;
	} else {
	    return calculus;
	}
    }
}
