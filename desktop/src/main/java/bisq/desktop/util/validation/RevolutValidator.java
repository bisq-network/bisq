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

public final class RevolutValidator extends LengthValidator {
    public RevolutValidator() {
        // Not sure what are requirements for Revolut user names
        // Please keep in mind that even we force users to set user name at startup we should handle also the case
        // that the old accountID as phone number or email is displayed at the username text field and we do not
        // want to break validation in those cases. So being too strict on the validators might cause more troubles
        // as its worth...
        // UPDATE 04/2021: Revolut usernames could be edited (3-16 characters, lowercase a-z and numbers only)
        super(3, 100);
    }

    public ValidationResult validate(String input) {
        return super.validate(input);
    }

}
