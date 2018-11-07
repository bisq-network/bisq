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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

public class BSQ extends Coin {

    public BSQ(Network network, NetworkParameters networkParameters) {
        super("BSQ", "BSQ", new BSQAddressValidator(networkParameters), network);
    }


    public static class Mainnet extends BSQ {

        public Mainnet() {
            super(Network.MAINNET, MainNetParams.get());
        }
    }


    public static class Testnet extends BSQ {

        public Testnet() {
            super(Network.TESTNET, TestNet3Params.get());
        }
    }


    public static class Regtest extends BSQ {

        public Regtest() {
            super(Network.REGTEST, RegTestParams.get());
        }
    }


    public static class BSQAddressValidator extends Base58BitcoinAddressValidator {

        public BSQAddressValidator(NetworkParameters networkParameters) {
            super(networkParameters);
        }

        @Override
        public AddressValidationResult validate(String address) {
            if (!address.startsWith("B"))
                return AddressValidationResult.invalidAddress("BSQ address must start with 'B'");

            String addressAsBtc = address.substring(1, address.length());

            return super.validate(addressAsBtc);
        }
    }
}
