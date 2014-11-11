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

import io.bitsquare.BitsquareModule;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

import com.google.inject.Injector;

import org.springframework.core.env.Environment;

public class BitcoinModule extends BitsquareModule {

    public static final String BITCOIN_NETWORK_KEY = "bitcoin.network";
    public static final String DEFAULT_BITCOIN_NETWORK = BitcoinNetwork.TESTNET.toString();

    public BitcoinModule(Environment env) {
        super(env);
    }

    @Override
    protected void configure() {
        bind(WalletFacade.class).asEagerSingleton();
        bind(FeePolicy.class).asEagerSingleton();
        bind(BlockChainFacade.class).asEagerSingleton();
        bind(NetworkParameters.class).toInstance(network());
    }

    @Override
    protected void doClose(Injector injector) {
        injector.getInstance(WalletFacade.class).shutDown();
    }

    private NetworkParameters network() {
        BitcoinNetwork network = BitcoinNetwork.valueOf(
                env.getProperty(BITCOIN_NETWORK_KEY, DEFAULT_BITCOIN_NETWORK).toUpperCase());

        switch (network) {
            case MAINNET:
                return MainNetParams.get();
            case TESTNET:
                return TestNet3Params.get();
            case REGTEST:
                return RegTestParams.get();
            default:
                throw new IllegalArgumentException("Unknown bitcoin network: " + network);
        }
    }
}

