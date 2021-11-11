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
import bisq.core.util.validation.InputValidator;

import java.math.BigInteger;

import java.util.Locale;

// TODO Does not yet recognize special letters like ä, ö, ü, å, ... as invalid characters
public final class IBANValidator extends InputValidator {

    private String restrictToCountry = "";
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////
    public IBANValidator() {
    }

    public IBANValidator(String restrictToCountry) {
        this.restrictToCountry = restrictToCountry;
    }

    @Override
    public ValidationResult validate(String input) {
        // TODO Add validation for primary and secondary IDs according to the selected type

        // IBAN max 34 chars, shortest is Norwegian with 15 chars, BBAN may include letters
        // bic: max 11 char

        // check input length first
        if (isStringInRange(input, 15, 34)) {
            input = input.toUpperCase(Locale.ROOT); // ensure upper case

            // check if country code is letters and checksum numeric
            if (!(Character.isLetter(input.charAt(0)) && Character.isLetter(input.charAt(1))))
                return new ValidationResult(false, Res.get("validation.iban.invalidCountryCode"));
            if (restrictToCountry.length() > 0 && !restrictToCountry.equals(input.substring(0, 2)))
                return new ValidationResult(false, Res.get("validation.iban.invalidCountryCode"));
            if (!(Character.isDigit(input.charAt(2)) && Character.isDigit(input.charAt(3))))
                return new ValidationResult(false, Res.get("validation.iban.checkSumNotNumeric"));

            // reorder IBAN to format <account number> <country code> <checksum>
            String input2 = input.substring(4, input.length()) + input.substring(0, 4);

            // check if input is alphanumeric and count included letters
            int charCount = 0;
            char ch;
            for (int k = 0; k < input2.length(); k++) {
                ch = input2.charAt(k);
                if (Character.isLetter(ch))
                    charCount++;
                else if (!Character.isDigit(ch))
                    return (new ValidationResult(false, Res.get("validation.iban.nonNumericChars")));
            }

            // create final char array for checksum validation
            char[] charArray = new char[input2.length() + charCount];
            int i = 0;
            int tmp;
            for (int k = 0; k < input2.length(); k++) {
                ch = input2.charAt(k);
                if (Character.isLetter(ch)) {
                    tmp = ch - ('A' - 10); // letters are transformed to two digit numbers A->10, B->11, ...
                    String s = Integer.toString(tmp);
                    charArray[i++] = s.charAt(0); // insert transformed
                    charArray[i++] = s.charAt(1); // letters into char array
                } else charArray[i++] = ch; // transfer digits directly to char array
            }
            // System.out.print(Arrays.toString(charArray) + '\t');
            BigInteger bigInt = new BigInteger(new String(charArray));
            int result = bigInt.mod(new BigInteger(Integer.toString(97))).intValue();
            if (result == 1)
                return new ValidationResult(true);
            else
                return new ValidationResult(false, Res.get("validation.iban.checkSumInvalid"));
        }
        // return new ValidationResult(false, BSResources.get("validation.accountNrChars", "15 - 34"));
        return new ValidationResult(false, Res.get("validation.iban.invalidLength"));
    }
}
