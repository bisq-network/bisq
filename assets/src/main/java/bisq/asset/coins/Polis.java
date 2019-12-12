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

import java.util.regex.Pattern;

import bisq.asset.AddressValidationResult;
import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

public class Polis extends Coin {
    public Polis() {
        super("Polis", "POLIS", new PolisAddressValidator());
    }

    public static class PolisAddressValidator extends Base58BitcoinAddressValidator {

        public PolisAddressValidator() {
            super(new PolisMainNetParams());
        }

        private static final Pattern VALID_ADDRESS = Pattern.compile("^[P][a-km-zA-HJ-NP-Z1-9]{25,34}$");

        @Override
        public AddressValidationResult validate(String address) {
            if (!VALID_ADDRESS.matcher(address).matches()) {
                return AddressValidationResult.invalidStructure();
            }
            return super.validate(address);
        }
    }

    public static class PolisMainNetParams extends NetworkParametersAdapter {

        public PolisMainNetParams() {
            this.addressHeader = 55;
            this.p2shHeader = 56;
            this.acceptableAddressCodes = new int[]{this.addressHeader, this.p2shHeader};
        }
    }
}
