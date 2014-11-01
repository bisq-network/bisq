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

import com.google.inject.AbstractModule;

import java.util.Properties;

public class BitcoinModule extends AbstractModule {

    private final Properties properties;
    private final BitcoinNetwork defaultNetwork;

    public BitcoinModule(Properties properties) {
        this(properties, BitcoinNetwork.TESTNET);
    }

    public BitcoinModule(Properties properties, BitcoinNetwork defaultNetwork) {
        this.properties = properties;
        this.defaultNetwork = defaultNetwork;
    }

    @Override
    protected void configure() {
        bind(WalletFacade.class).asEagerSingleton();
        bind(FeePolicy.class).asEagerSingleton();
        bind(BlockChainFacade.class).asEagerSingleton();
        bind(NetworkParameters.class).toInstance(network());
    }

    private NetworkParameters network() {
            String networkName = properties.getProperty("networkType", defaultNetwork.name());

            switch (BitcoinNetwork.valueOf(networkName.toUpperCase())) {
                case MAINNET:
                    return MainNetParams.get();
                case TESTNET:
                    return TestNet3Params.get();
                case REGTEST:
                    return RegTestParams.get();
                default:
                    throw new IllegalArgumentException("Unknown bitcoin network name: " + networkName);
            }
    }

}

