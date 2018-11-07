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

public class InternetOfPeople extends Coin {

    public InternetOfPeople() {
        super("Internet of People", "IOP", new InternetOfPeopleAddressValidator());
    }


    public static class InternetOfPeopleAddressValidator extends Base58BitcoinAddressValidator {

        public InternetOfPeopleAddressValidator() {
            super(new InternetOfPeopleParams());
        }

        @Override
        public AddressValidationResult validate(String address) {
            if (!address.matches("^[p][a-km-zA-HJ-NP-Z1-9]{25,34}$"))
                return AddressValidationResult.invalidStructure();

            return super.validate(address);
        }
    }


    public static class InternetOfPeopleParams extends NetworkParametersAdapter {

        public InternetOfPeopleParams() {
            super();
            addressHeader = 117;
            p2shHeader = 174;
            acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        }
    }
}
