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


import io.bitsquare.gui.util.validation.altcoins.ByteballAddressValidator;
import io.bitsquare.gui.util.validation.params.IOPParams;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.params.MainNetParams;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AltCoinAddressValidator extends InputValidator {
    private static final Logger log = LoggerFactory.getLogger(AltCoinAddressValidator.class);

    private String currencyCode;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    @Override
    public ValidationResult validate(String input) {
        ValidationResult validationResult = super.validate(input);
        if (!validationResult.isValid || currencyCode == null) {
            return validationResult;
        } else {

            // Validation: 
            // 1: With a regex checking the correct structure of an address 
            // 2: If the address contains a checksum, verify the checksum

            ValidationResult wrongChecksum = new ValidationResult(false, "Address validation failed because checksum was not correct.");
            ValidationResult regexTestFailed = new ValidationResult(false, "Address validation failed because it does not match the structure of a " + currencyCode + " address.");

            switch (currencyCode) {
                // Example for BTC, though for BTC we use the BitcoinJ library address check
                case "BTC":
                    log.error("" + input.length());
                    // taken form: https://stackoverflow.com/questions/21683680/regex-to-match-bitcoin-addresses
                    if (input.matches("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
                        if (verifyChecksum(input))
                            try {
                                new Address(MainNetParams.get(), input);
                                return new ValidationResult(true);
                            } catch (AddressFormatException e) {
                                return new ValidationResult(false, getErrorMessage(e));
                            }
                        else
                            return wrongChecksum;
                    } else {
                        return regexTestFailed;
                    }
                case "IOP":
                    if (input.matches("^[p][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
                        if (verifyChecksum(input)) {
                            try {
                                new Address(IOPParams.get(), input);
                                return new ValidationResult(true);
                            } catch (AddressFormatException e) {
                                return new ValidationResult(false, getErrorMessage(e));
                            }
                        } else {
                            return wrongChecksum;
                        }
                    } else {
                        return regexTestFailed;
                    }
                case "ZEC":
                    // We only support t addresses (transparent transactions)
                    if (input.startsWith("t"))
                        return validationResult;
                    else
                        return new ValidationResult(false, "ZEC address need to start with t. Addresses starting with z are not supported.");
                case "GBYTE":
                    return ByteballAddressValidator.validate(input);
                default:
                    log.debug("Validation for AltCoinAddress not implemented yet. currencyCode:" + currencyCode);
                    return validationResult;
            }
        }
    }

    @NotNull
    private String getErrorMessage(AddressFormatException e) {
        return "Address is not a valid " + currencyCode + " address! " + e.getMessage();
    }

    private boolean verifyChecksum(String input) {
        // TODO
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
