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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;

/**
 * {@link AddressValidator} for Bitcoin addresses.
 *
 * @author Oscar Guindzberg
 */
public class BitcoinAddressValidator implements AddressValidator {

    private final NetworkParameters networkParameters;

    public BitcoinAddressValidator() {
        this(MainNetParams.get());
    }

    public BitcoinAddressValidator(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }

    @Override
    public AddressValidationResult validate(String address) {
        try {
            Address.fromString(networkParameters, address);
        } catch (AddressFormatException ex) {
            return AddressValidationResult.invalidAddress(ex);
        }

        return AddressValidationResult.validAddress();
    }
}
