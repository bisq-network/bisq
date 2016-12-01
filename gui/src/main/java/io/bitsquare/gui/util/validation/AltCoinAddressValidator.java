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


public final class AltCoinAddressValidator extends InputValidator {

    private String currencyCode;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    @Override
    public ValidationResult validate(String input) {
        if (currencyCode != null) {
            switch (currencyCode) {
                case "ZEC":
                    if (input != null && input.startsWith("t"))
                        return super.validate(input);
                    else
                        return new ValidationResult(false, "ZEC address need to start with t. Addresses starting with z are not supported.");
                default:
                    return super.validate(input);
            }
        } else {
            return super.validate(input);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
