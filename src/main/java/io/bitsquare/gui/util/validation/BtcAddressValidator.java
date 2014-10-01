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

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BtcValidator for validating BTC values.
 * <p>
 * That class implements just what we need for the moment. It is not intended as a general purpose library class.
 */
public final class BtcAddressValidator extends InputValidator {
    private static final Logger log = LoggerFactory.getLogger(BtcAddressValidator.class);
    private NetworkParameters networkParameters;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BtcAddressValidator(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ValidationResult validate(String input) {

        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid)
            return validateBtcAddress(input);
        else
            return result;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ValidationResult validateBtcAddress(String input) {
        try {
            new Address(networkParameters, input);
            return new ValidationResult(true);
        } catch (AddressFormatException e) {
            return new ValidationResult(false, "Bitcoin address is a valid format");
        }
    }
}
