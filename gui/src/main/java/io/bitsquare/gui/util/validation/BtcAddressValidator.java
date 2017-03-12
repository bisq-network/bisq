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

import io.bitsquare.messages.user.Preferences;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;

import javax.inject.Inject;

public final class BtcAddressValidator extends InputValidator {

    private final Preferences preferences;

    @Inject
    public BtcAddressValidator(Preferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public ValidationResult validate(String input) {

        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid)
            return validateBtcAddress(input);
        else
            return result;
    }

    private ValidationResult validateBtcAddress(String input) {
        try {
            new Address(preferences.getBitcoinNetwork().getParameters(), input);
            return new ValidationResult(true);
        } catch (AddressFormatException e) {
            return new ValidationResult(false, Res.get("validation.btc.invalidFormat"));
        }
    }
}
