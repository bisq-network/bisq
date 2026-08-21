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

import bisq.asset.*;

public class Veil extends Coin {

    public Veil() {
        super("Veil", "VEIL", new VeilAddressValidator());
    }

    public static class VeilAddressValidator extends Base58AddressValidator {

        public VeilAddressValidator() {
            super(new VeilParams());
        }

        @Override
        public AddressValidationResult validate(String address) {
            if (address.startsWith("V")) {
                return super.validate(address);
            }else if (address.startsWith("bv")){
                // TODO: Add bech32 support
                return AddressValidationResult.invalidAddress("Bech32 addresses not supported on bisq");
            }
            return AddressValidationResult.invalidStructure();
        }
    }

    public static class VeilParams extends NetworkParametersAdapter {

        public VeilParams() {
            addressHeader = 70;
            p2shHeader = 5;
        }
    }
}

