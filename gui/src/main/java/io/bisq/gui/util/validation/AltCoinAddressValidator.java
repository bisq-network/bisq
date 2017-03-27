/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.util.validation;


import io.bisq.common.locale.Res;
import io.bisq.gui.util.validation.altcoins.ByteballAddressValidator;
import io.bisq.gui.util.validation.params.IOPParams;
import io.bisq.gui.util.validation.params.PivxParams;
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

            ValidationResult wrongChecksum = new ValidationResult(false,
                    Res.get("validation.altcoin.wrongChecksum"));
            ValidationResult regexTestFailed = new ValidationResult(false,
                    Res.get("validation.altcoin.wrongStructure", currencyCode));

            switch (currencyCode) {
                case "ETH":
                    // https://github.com/ethereum/web3.js/blob/master/lib/utils/utils.js#L403
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))                
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);                
                // Example for BTC, though for BTC we use the BitcoinJ library address check
                case "BTC":
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
                case "PIVX":
                    if (input.matches("^[D][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
                        if (verifyChecksum(input)) {
                            try {
                                new Address(PivxParams.get(), input);
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
                        return new ValidationResult(false, Res.get("validation.altcoin.zAddressesNotSupported"));

                    // TODO test not successful
                /*case "XTO":
                    if (input.matches("^[T2][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
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
                    }*/
                case "GBYTE":
                    return ByteballAddressValidator.validate(input);
                default:
                    log.debug("Validation for AltCoinAddress not implemented yet. currencyCode: " + currencyCode);
                    return validationResult;
            }
        }
    }

    @NotNull
    private String getErrorMessage(AddressFormatException e) {
        return Res.get("validation.altcoin.invalidAddress", currencyCode, e.getMessage());
    }

    private boolean verifyChecksum(String input) {
        // TODO
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
