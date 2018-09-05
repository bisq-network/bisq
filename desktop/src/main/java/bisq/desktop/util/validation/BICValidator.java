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

import java.util.Locale;

/*
 * BIC information taken from German wikipedia (2017-01-30)
 *
 * length 8 or 11 characters
 * General format: BBBB CC LL (bbb)
 * with	B - Bank code
 *	C - Country code
 *	L - Location code
 *	b - branch code (if applicable)
 *
 * B and C must be letters
 * first L cannot be 0 or 1, second L cannot be O (upper case 'o')
 * bbb cannot begin with X, unless it is XXX
 */

// TODO Special letters like ä, å, ... are not detected as invalid

public final class BICValidator extends InputValidator {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ValidationResult validate(String input) {
        // TODO Add validation for primary and secondary IDs according to the selected type

        // IBAN max 34 chars
        // bic: 8 or 11 chars

        // check ensure length 8 or 11
        if (!isStringWithFixedLength(input, 8) && !isStringWithFixedLength(input, 11))
            return new ValidationResult(false, Res.get("validation.bic.invalidLength"));

        input = input.toUpperCase(Locale.ROOT);

        // ensure Bank and Country code to be letters only
        for (int k = 0; k < 6; k++) {
            if (!Character.isLetter(input.charAt(k)))
                return new ValidationResult(false, Res.get("validation.bic.letters"));
        }

        // ensure location code starts not with 0 or 1 and ends not with O
        char ch = input.charAt(6);
        if (ch == '0' || ch == '1' || input.charAt(7) == 'O')
            return new ValidationResult(false, Res.get("validation.bic.invalidLocationCode"));

        if (input.startsWith("REVO"))
            return new ValidationResult(false, Res.get("validation.bic.sepaRevolutBic"));


        // check complete for 8 char BIC
        if (input.length() == 8)
            return new ValidationResult(true);

        // ensure branch code does not start with X unless it is XXX
        if (input.charAt(8) == 'X')
            if (input.charAt(9) != 'X' || input.charAt(10) != 'X')
                return new ValidationResult(false, Res.get("validation.bic.invalidBranchCode"));

        return new ValidationResult(true);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////
}
