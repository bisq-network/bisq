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

/*
 * Mail addresses consist of localPart @ domainPart
 *
 * Local part:
 * May contain lots of symbols A-Za-z0-9.!#$%&'*+-/=?^_`{|}~
 * but cannot begin or end with a dot (.)
 * between double quotes many more symbols are allowed:
 * "(),:;<>@[\] (ASCII: 32, 34, 40, 41, 44, 58, 59, 60, 62, 64, 91–93)
 *
 * Domain part:
 * Consists of name dot TLD
 * name can but usually doesn't (compatibility reasons) contain non-ASCII
 * symbols.
 * TLD is at least two letters long
 */
public final class EmailValidator extends InputValidator {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final ValidationResult invalidAddress = new ValidationResult(false, Res.get("validation.email.invalidAddress"));

    @Override
    public ValidationResult validate(String input) {
        if (input == null || input.length() < 6 || input.length() > 100) // shortest address is l@d.cc, max length 100
            return invalidAddress;
        String[] subStrings;
        String local, domain;

        subStrings = input.split("@", -1);

        if (subStrings.length == 1) // address does not contain '@'
            return invalidAddress;
        if (subStrings.length > 2) // multiple @'s included -> check for valid double quotes
            if (!checkForValidQuotes(subStrings)) // around @'s -> "..@..@.." and concatenate local part
                return invalidAddress;
        local = subStrings[0];
        domain = subStrings[subStrings.length - 1];

        if (local.isEmpty())
            return invalidAddress;

        // local part cannot begin or end with '.'
        if (local.startsWith(".") || local.endsWith("."))
            return invalidAddress;

        String[] splitDomain = domain.split("\\.", -1); // '.' is a regex in java and has to be escaped
        String tld = splitDomain[splitDomain.length - 1];
        if (splitDomain.length < 2)
            return invalidAddress;

        if (splitDomain[0] == null || splitDomain[0].isEmpty())
            return invalidAddress;

        // TLD length is at least two
        if (tld.length() < 2)
            return invalidAddress;

        // TLD is letters only
        for (int k = 0; k < tld.length(); k++)
            if (!Character.isLetter(tld.charAt(k)))
                return invalidAddress;
        return new ValidationResult(true);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean checkForValidQuotes(String[] subStrings) {
        int length = subStrings.length - 2; // is index on last substring of local part

        // check for odd number of double quotes before first and after last '@'
        if ((subStrings[0].split("\"", -1).length % 2 == 1) || (subStrings[length].split("\"", -1).length % 2 == 1))
            return false;
        for (int k = 1; k < length; k++) {
            if (subStrings[k].split("\"", -1).length % 2 == 0)
                return false;
        }

        String patchLocal = "";
        for (int k = 0; k <= length; k++) // remember: length is last index not array length
            patchLocal = patchLocal.concat(subStrings[k]); // @'s are not reinstalled, since not needed for further checks
        subStrings[0] = patchLocal;
        return true;
    }
}
