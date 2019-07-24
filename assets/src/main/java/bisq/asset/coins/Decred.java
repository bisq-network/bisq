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

public class Decred extends Coin {

	public Decred(Network network, NetworkParameters networkParameters) {
		super("Decred", "DCR", new DcrAddressValidator(networkParameters), network);
	}

	public static class DcrAddressValidator extends Base58BitcoinAddressValidator {

		public DcrAddressValidator(NetworkParameters networkParameters) {
			super(networkParameters);
		}

		@Override
		public AddressValidationResult validate(String address) {
			if (address == null || address.length() < 26 || address.length() > 36 || !address.startsWith("Dk")
					|| !address.startsWith("Ds") || !address.startsWith("De") || !address.startsWith("DS")
					|| !address.startsWith("Dc") || !address.startsWith("Pm")) {
				return AddressValidationResult
						.invalidAddress("DCR address must start with 'Dk' or 'Ds' or 'De' or 'DS' or 'Dc' or 'Pm' and must have 34 characters.");
			}

			String addressAsBtc = address.substring(1, address.length());

			return super.validate(addressAsBtc);
		}
	}
}
