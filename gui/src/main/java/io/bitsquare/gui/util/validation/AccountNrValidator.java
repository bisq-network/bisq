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

public final class AccountNrValidator extends BankValidator {

    @Override
    public ValidationResult validate(String input) {
        switch (countryCode) {
            case "GB":
                try {
                    Integer.parseInt(input);
                    if (input.length() != 8) {
                        return new ValidationResult(false, BSResources.get("validation.ukAccountNr"));
                    } else {
                        return super.validate(input);
                    }
                } catch (Throwable t) {
                    return new ValidationResult(false, BSResources.get("validation.ukAccountNr"));
                }
            default:
                return super.validate(input);
        }

    }
}
