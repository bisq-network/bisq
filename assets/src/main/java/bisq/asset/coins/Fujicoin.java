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

import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

public class Fujicoin extends Coin {
    public Fujicoin() {
        super("Fujicoin", "FJC", new Base58BitcoinAddressValidator(new FujicoinParams()));
    }
	   public static class FujicoinParams extends NetworkParametersAdapter {

        public FujicoinParams() {
            addressHeader = 36;
            p2shHeader = 16;
            acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        }
    }
}
