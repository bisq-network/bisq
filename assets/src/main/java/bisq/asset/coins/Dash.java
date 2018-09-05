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

import org.libdohj.params.DashMainNetParams;
import org.libdohj.params.DashRegTestParams;
import org.libdohj.params.DashTestNet3Params;

import org.bitcoinj.core.NetworkParameters;

public abstract class Dash extends Coin {

    public Dash(Network network, NetworkParameters networkParameters) {
        super("Dash", "DASH", new Base58BitcoinAddressValidator(networkParameters), network);
    }


    public static class Mainnet extends Dash {

        public Mainnet() {
            super(Network.MAINNET, DashMainNetParams.get());
        }
    }


    public static class Testnet extends Dash {

        public Testnet() {
            super(Network.TESTNET, DashTestNet3Params.get());
        }
    }


    public static class Regtest extends Dash {

        public Regtest() {
            super(Network.REGTEST, DashRegTestParams.get());
        }
    }
}
