/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

public enum BitcoinNetwork {
    MAINNET(MainNetParams.get()),
    TESTNET(TestNet3Params.get()),
    REGTEST(RegTestParams.get());

    public static final String KEY = "bitcoinNetwork";
    public static final BitcoinNetwork DEFAULT = MAINNET;

    private final NetworkParameters parameters;

    BitcoinNetwork(NetworkParameters parameters) {
        this.parameters = parameters;
    }

    public NetworkParameters getParameters() {
        return parameters;
    }
}
