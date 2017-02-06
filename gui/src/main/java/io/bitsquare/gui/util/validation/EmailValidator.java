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
 *
 */

public final class EmailValidator extends InputValidator {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final ValidationResult invalidAddress = new ValidationResult(false, "Invalid address");

    @Override
    public ValidationResult validate(String input) {
        // TODO
	if (input == null || input.length() < 6) // shortest address is l@d.cc
		return invalidAddress;
	String [] substrings;
	String local, domain;

	substrings = input.split("@",-1);

	if (substrings.length == 1) // address does not contain '@'
		return invalidAddress;
	if (substrings.length > 2) // multiple @'s included -> check for valid double quotes
		if (!checkForValidQuotes(substrings)) // around @'s -> "..@..@.." and concatenate local part
			return invalidAddress;
	local = substrings[0];
	domain = substrings[substrings.length-1];

	// local part cannot begin or end with '.'
	if (local.startsWith(".") || local.endsWith("."))
		return invalidAddress;

	String [] splitDomain = domain.split("\\.", -1); // '.' is a regex in java and has to be escaped
	String tld = splitDomain[splitDomain.length-1];
	if (splitDomain.length < 2)
		return invalidAddress;

	if (splitDomain[0] == null || splitDomain[0].isEmpty())
		return invalidAddress;

	// TLD length is at least two
	if (tld.length() < 2)
		return invalidAddress;

	// TLD is letters only
	for (int k=0; k<tld.length(); k++)
		if (!Character.isLetter(tld.charAt(k)))
			return invalidAddress;
	return new ValidationResult(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean checkForValidQuotes(String [] substrings) {
	int length = substrings.length - 2; // is index on last substring of local part

	// check for odd number of double quotes before first and after last '@'
	if ( (substrings[0].split("\"",-1).length %2 == 1) || (substrings[length].split("\"",-1).length %2 == 1) )
		return false;
	for (int k=1; k<length; k++) {
		if (substrings[k].split("\"",-1).length % 2 == 0)
			return false;
	}

	String patchLocal = new String("");
	for (int k=0; k<=length; k++) // remember: length is last index not array length
		patchLocal = patchLocal.concat(substrings[k]); // @'s are not reinstalled, since not needed for further checks
	substrings[0] = patchLocal;
	return true;
    }

}
