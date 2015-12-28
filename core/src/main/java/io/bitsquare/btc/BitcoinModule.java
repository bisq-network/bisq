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

import com.google.inject.Singleton;
import io.bitsquare.app.AppModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.io.File;

import static com.google.inject.name.Names.named;

public class BitcoinModule extends AppModule {
    private static final Logger log = LoggerFactory.getLogger(BitcoinModule.class);

    public BitcoinModule(Environment env) {
        super(env);
    }

    @Override
    protected void configure() {
        bind(RegTestHost.class).toInstance(env.getProperty(RegTestHost.KEY, RegTestHost.class, RegTestHost.DEFAULT));
        bind(FeePolicy.class).in(Singleton.class);

        bindConstant().annotatedWith(named(UserAgent.NAME_KEY)).to(env.getRequiredProperty(UserAgent.NAME_KEY));
        bindConstant().annotatedWith(named(UserAgent.VERSION_KEY)).to(env.getRequiredProperty(UserAgent.VERSION_KEY));
        bind(UserAgent.class).in(Singleton.class);

        File walletDir = new File(env.getRequiredProperty(WalletService.DIR_KEY));
        bind(File.class).annotatedWith(named(WalletService.DIR_KEY)).toInstance(walletDir);

        bind(AddressEntryList.class).in(Singleton.class);
        bind(TradeWalletService.class).in(Singleton.class);
        bind(WalletService.class).in(Singleton.class);
    }
}

