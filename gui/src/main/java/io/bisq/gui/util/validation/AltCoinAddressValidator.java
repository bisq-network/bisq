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

package io.bisq.gui.util.validation;


import io.bisq.common.locale.Res;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.gui.util.validation.altcoins.ByteballAddressValidator;
import io.bisq.gui.util.validation.altcoins.NxtReedSolomonValidator;
import io.bisq.gui.util.validation.altcoins.OctocoinAddressValidator;
import io.bisq.gui.util.validation.params.IOPParams;
import io.bisq.gui.util.validation.params.OctocoinParams;
import io.bisq.gui.util.validation.params.PivxParams;
import io.bisq.gui.util.validation.params.btc.BtcMainNetParams;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.jetbrains.annotations.NotNull;
import org.libdohj.params.*;

@Slf4j
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
                case "BTC":
                    try {
                        switch (BisqEnvironment.getBaseCurrencyNetwork()) {
                            case BTC_MAINNET:
                                Address.fromBase58(MainNetParams.get(), input);
                                break;
                            case BTC_TESTNET:
                                Address.fromBase58(TestNet3Params.get(), input);
                                break;
                            case BTC_REGTEST:
                                Address.fromBase58(RegTestParams.get(), input);
                                break;
                            case LTC_MAINNET:
                            case LTC_TESTNET:
                            case LTC_REGTEST:
                            case DOGE_MAINNET:
                            case DOGE_TESTNET:
                            case DOGE_REGTEST:
                                Address.fromBase58(BtcMainNetParams.get(), input);
                                return new ValidationResult(true);
                        }
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "LTC":
                    try {
                        switch (BisqEnvironment.getBaseCurrencyNetwork()) {
                            case BTC_MAINNET:
                            case BTC_TESTNET:
                            case BTC_REGTEST:
                            case DOGE_MAINNET:
                            case DOGE_TESTNET:
                            case DOGE_REGTEST:
                            case LTC_MAINNET:
                                Address.fromBase58(LitecoinMainNetParams.get(), input);
                                break;
                            case LTC_TESTNET:
                                Address.fromBase58(LitecoinTestNet3Params.get(), input);
                                break;
                            case LTC_REGTEST:
                                Address.fromBase58(LitecoinRegTestParams.get(), input);
                                break;
                        }
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "DOGE":
                    try {
                        switch (BisqEnvironment.getBaseCurrencyNetwork()) {
                            case BTC_MAINNET:
                            case BTC_TESTNET:
                            case BTC_REGTEST:
                            case LTC_MAINNET:
                            case LTC_TESTNET:
                            case LTC_REGTEST:
                            case DOGE_MAINNET:
                                Address.fromBase58(DogecoinMainNetParams.get(), input);
                                break;
                            case DOGE_TESTNET:
                                Address.fromBase58(DogecoinTestNet3Params.get(), input);
                                break;
                            case DOGE_REGTEST:
                                Address.fromBase58(DogecoinRegTestParams.get(), input);
                                break;
                        }
                        return new ValidationResult(true);
                    } catch (AddressFormatException e) {
                        return new ValidationResult(false, getErrorMessage(e));
                    }
                case "ETH":
                    // https://github.com/ethereum/web3.js/blob/master/lib/utils/utils.js#L403
                    if (!input.matches("^(0x)?[0-9a-fA-F]{40}$"))
                        return regexTestFailed;
                    else
                        return new ValidationResult(true);
                    // Example for BTC, though for BTC we use the BitcoinJ library address check
                case "PIVX":
                    if (input.matches("^[D][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
                        //noinspection ConstantConditions
                        if (verifyChecksum(input)) {
                            try {
                                Address.fromBase58(PivxParams.get(), input);
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
                        //noinspection ConstantConditions
                        if (verifyChecksum(input)) {
                            try {
                                Address.fromBase58(IOPParams.get(), input);
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
                case "888":
                    if (input.matches("^[83][a-km-zA-HJ-NP-Z1-9]{25,34}$")) {
                        if (OctocoinAddressValidator.ValidateAddress(input)) {
                            try {
                                Address.fromBase58(OctocoinParams.get(), input);
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
                case "GBYTE":
                    return ByteballAddressValidator.validate(input);
                case "NXT":
                    if (!input.startsWith("NXT-") || !input.equals(input.toUpperCase())) {
                        return regexTestFailed;
                    }
                    try {
                        long accountId = NxtReedSolomonValidator.decode(input.substring(4));
                        return new ValidationResult(accountId != 0);
                    } catch (NxtReedSolomonValidator.DecodeException e) {
                        return wrongChecksum;
                    }
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

    @SuppressWarnings({"UnusedParameters", "SameReturnValue"})
    private boolean verifyChecksum(String input) {
        // TODO
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
