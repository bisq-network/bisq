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

import org.libdohj.params.LitecoinMainNetParams;
import org.libdohj.params.LitecoinRegTestParams;
import org.libdohj.params.LitecoinTestNet3Params;

import org.bitcoinj.core.NetworkParameters;

public abstract class Litecoin extends Coin {

    public Litecoin(Network network, NetworkParameters networkParameters) {
        super("Litecoin", "LTC", new Base58BitcoinAddressValidator(networkParameters), network);
    }


    public static class Mainnet extends Litecoin {

        public Mainnet() {
            super(Network.MAINNET, LitecoinMainNetParams.get());
        }
    }


    public static class Testnet extends Litecoin {

        public Testnet() {
            super(Network.TESTNET, LitecoinTestNet3Params.get());
        }
    }


    public static class Regtest extends Litecoin {

        public Regtest() {
            super(Network.REGTEST, LitecoinRegTestParams.get());
        }
    }
}
