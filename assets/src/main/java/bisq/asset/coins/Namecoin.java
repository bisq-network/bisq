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

import bisq.core.locale.Res;

public class Namecoin extends Coin {

    public Namecoin(Network network, NetworkParameters networkParameters) {
        super("Namecoin", "NMC", new NmcAddressValidator(networkParameters), network);
    }

    public static class NmcAddressValidator extends Base58BitcoinAddressValidator {

        public NmcAddressValidator(NetworkParameters networkParameters) {
            super(networkParameters);
        }

        @Override
        public AddressValidationResult validate(String address) {
            if (address == null || address.length() != 34 || !address.startsWith("N") || !address.startsWith("M"))
                return AddressValidationResult.invalidAddress(Res.get("account.altcoin.popup.validation.NMC"));

            String addressAsBtc = address.substring(1, address.length());

            return super.validate(addressAsBtc);
        }
    }
}
