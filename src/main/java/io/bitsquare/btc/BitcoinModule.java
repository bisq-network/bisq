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

import java.io.File;

import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

public class BitcoinModule extends BitsquareModule {


    public BitcoinModule(Environment env) {
        super(env);
    }

    @Override
    protected void configure() {
        bind(BitcoinNetwork.class).toInstance(
            env.getProperty(BitcoinNetwork.KEY, BitcoinNetwork.class, BitcoinNetwork.DEFAULT));
        bind(FeePolicy.class).asEagerSingleton();

        bindConstant().annotatedWith(named(UserAgent.NAME_KEY)).to(env.getRequiredProperty(UserAgent.NAME_KEY));
        bindConstant().annotatedWith(named(UserAgent.VERSION_KEY)).to(env.getRequiredProperty(UserAgent.VERSION_KEY));
        bind(UserAgent.class).asEagerSingleton();

        File walletDir = new File(env.getRequiredProperty(WalletService.DIR_KEY));
        bind(File.class).annotatedWith(named(WalletService.DIR_KEY)).toInstance(walletDir);
        bindConstant().annotatedWith(named(WalletService.PREFIX_KEY)).to(
                env.getRequiredProperty(WalletService.PREFIX_KEY));
        bind(WalletService.class).asEagerSingleton();

        bind(BlockChainService.class).asEagerSingleton();
    }

    @Override
    protected void doClose(Injector injector) {
        injector.getInstance(WalletService.class).shutDown();
    }
}

