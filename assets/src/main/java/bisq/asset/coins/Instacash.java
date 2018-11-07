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
import bisq.asset.AddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;

public class Instacash extends Coin {

    public Instacash() {
        super("Instacash", "ICH", new InstacashAddressValidator());
    }


    public static class InstacashAddressValidator implements AddressValidator {

        @Override
        public AddressValidationResult validate(String address) {
            if (!address.matches("^[A][a-km-zA-HJ-NP-Z1-9]{25,34}$"))
                return AddressValidationResult.invalidStructure();

            try {
                Address.fromBase58(new InstacashParams(), address);
            } catch (AddressFormatException ex) {
                return AddressValidationResult.invalidAddress(ex);
            }

            return AddressValidationResult.validAddress();
        }
    }


    public static class InstacashParams extends NetworkParametersAdapter {

        public InstacashParams() {
            addressHeader = 23;
            p2shHeader = 13;
            acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        }
    }
}
