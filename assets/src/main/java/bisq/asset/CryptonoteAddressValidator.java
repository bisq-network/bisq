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

package bisq.asset;

/**
 * {@link AddressValidator} for Base58-encoded Cryptonote addresses.
 *
 * @author Chris Beams
 * @since 0.7.0
 */
public class CryptonoteAddressValidator implements AddressValidator {

    private final String prefix;
    private final String subAddressPrefix;
    private final String validCharactersRegex = "^[1-9A-HJ-NP-Za-km-z]+$";

    public CryptonoteAddressValidator(String prefix, String subAddressPrefix) {
        this.prefix = prefix;
        this.subAddressPrefix = subAddressPrefix;
    }

    @Override
    public AddressValidationResult validate(String address) {
        if (!address.matches(validCharactersRegex)) {
            // Invalid characters
            return AddressValidationResult.invalidStructure();
        }

        if (address.startsWith(prefix) && address.length() == 94 + prefix.length()) {
            // Standard address
            return AddressValidationResult.validAddress();
        } else if (address.startsWith(subAddressPrefix) && address.length() == 94 + subAddressPrefix.length()) {
            // Subaddress
            return AddressValidationResult.validAddress();
        } else {
            // Integrated? Invalid? Doesn't matter
            return AddressValidationResult.invalidStructure();
        }
    }
}
