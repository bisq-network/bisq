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

import bisq.asset.BitcoinAddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

public class WAMCoin extends Coin {
    public WAMCoin() {
        super("WAM Coin", "WAM", new BitcoinAddressValidator(new WAMCoinMainNetParams()), Network.MAINNET);
    }

    public static class WAMCoinMainNetParams extends NetworkParametersAdapter {
        public WAMCoinMainNetParams() {
            this.addressHeader = 73;         // 'W'
            this.p2shHeader = 135;           // 'w'
            this.segwitAddressHrp = "wam";
        }
    }
}
