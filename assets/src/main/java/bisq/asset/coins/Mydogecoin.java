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

import bisq.asset.Base58AddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

public class Mydogecoin extends Coin {

    public Mydogecoin() {
        super("MYdogecoin", "MYDOGE", new Base58AddressValidator(new MydogecoinMainNetParams()), Network.MAINNET);
    }

    public static class MydogecoinMainNetParams extends NetworkParametersAdapter {
        public MydogecoinMainNetParams() {
            this.addressHeader = 51;
            this.p2shHeader = 30;
        }
    }
}
