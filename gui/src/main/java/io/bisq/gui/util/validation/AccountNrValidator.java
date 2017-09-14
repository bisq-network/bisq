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

package io.bisq.gui.util.validation;


import io.bisq.common.locale.BankUtil;
import io.bisq.common.locale.Res;
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
                    return new ValidationResult(false, Res.get("validation.accountNr", length));
            case "US":
                if (isNumberInRange(input, 4, 17))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.accountNr", "4 - 17"));
            case "BR":
                if (isStringInRange(input, 1, 20))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.accountNrChars", "1 - 20"));
            case "NZ":
                input2 = input != null ? input.replaceAll("-", "") : null;
                if (isNumberInRange(input2, 15, 16))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.accountNrFormat", "03-1587-0050000-00"));
            case "AU":
                if (isNumberInRange(input, 4, 10))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.accountNr", "4 - 10"));
            case "CA":
                if (isNumberInRange(input, 7, 12))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.accountNr", "7 - 12"));
            case "MX":
                length = 18;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.sortCodeNumber", getLabel(), length));
            case "HK":
                input2 = input != null ? input.replaceAll("-", "") : null;
                if (isNumberInRange(input2, 9, 12))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.accountNrFormat", "005-231289-112"));
            case "NO":
                if (input != null) {
                    length = 11;
                    // Provided by sturles:
                    // https://github.com/bisq-network/exchange/pull/707

                    // https://no.wikipedia.org/wiki/MOD11#Implementasjoner_i_forskjellige_programmeringspr.C3.A5k
                    // https://en.wikipedia.org/wiki/International_Bank_Account_Number#Generating_IBAN_check_digits6

                    // 11 digits, last digit is checksum.  Checksum algoritm is 
                    // MOD11 with weights 2,3,4,5,6,7,2,3,4,5 right to left.
                    // First remove whitespace and periods.  Normal formatting is: 
                    // 1234.56.78903
                    input2 = StringUtils.remove(input, " ");
                    input2 = StringUtils.remove(input2, ".");
                    // 11 digits, numbers only
                    if (input2.length() != length || !StringUtils.isNumeric(input2))
                        return new ValidationResult(false, Res.get("validation.sortCodeNumber", getLabel(), length));
                    int lastDigit = Character.getNumericValue(input2.charAt(input2.length() - 1));
                    if (getMod11ControlDigit(input2) != lastDigit)
                        return new ValidationResult(false, "Kontonummer har feil sjekksum"); // not translated
                    else
                        return super.validate(input);
                } else {
                    return super.validate(null);
                }
            default:
                return super.validate(input);
        }

    }

    private int getMod11ControlDigit(String accountNrString) {
        int sumForMod = 0;
        int controlNumber = 2;
        char[] accountNr = accountNrString.toCharArray();

        for (int i = accountNr.length - 2; i >= 0; i--) {
            sumForMod += (Character.getNumericValue(accountNr[i]) * controlNumber);
            controlNumber++;

            if (controlNumber > 7) {
                controlNumber = 2;
            }
        }
        int calculus = (11 - sumForMod % 11);
        if (calculus == 11) {
            return 0;
        } else {
            return calculus;
        }
    }

    private String getLabel() {
        String label = BankUtil.getAccountNrLabel(countryCode);
        return label.substring(0, label.length() - 1);
    }
}
