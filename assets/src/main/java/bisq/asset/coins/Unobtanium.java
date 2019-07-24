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

package bisq.asset.coins;

import org.bitcoinj.core.NetworkParameters;

import bisq.asset.AddressValidationResult;
import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;

public class Unobtanium extends Coin {

    public Unobtanium(Network network, NetworkParameters networkParameters) {
        super("Unobtanium", "UNO", new UnoAddressValidator(networkParameters), network);
    }
    
    public static class UnoAddressValidator extends Base58BitcoinAddressValidator {

        public UnoAddressValidator(NetworkParameters networkParameters) {
            super(networkParameters);
        }

        @Override
        public AddressValidationResult validate(String address) {
            if (address == null || address.length() != 34 || !address.startsWith("u")) {
                return AddressValidationResult.invalidAddress("UNO address must start with 'u' and must have 34 characters.");
            }

            String addressAsBtc = address.substring(1, address.length());

            return super.validate(addressAsBtc);
        }
    }
}
