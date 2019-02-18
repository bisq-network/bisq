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

import bisq.asset.AddressValidationResult;
import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

public class DeepOnion extends Coin {
    public DeepOnion() {
        super("DeepOnion", "ONION", new DeepOnionAddressValidator());
    }

    public static class DeepOnionAddressValidator extends Base58BitcoinAddressValidator {

        public DeepOnionAddressValidator() {
            super(new DeepOnionParams());
        }

        @Override
        public AddressValidationResult validate(String address) {
            if (!address.matches("^[D][a-km-zA-HJ-NP-Z1-9]{24,33}$"))
                return AddressValidationResult.invalidStructure();

            return super.validate(address);
        }
    }

    public static class DeepOnionParams extends NetworkParametersAdapter {

        public DeepOnionParams() {
            super();
            addressHeader = 31;
            p2shHeader = 78;
            acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        }
    }
}
