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

package io.bitsquare.app;

import io.bitsquare.BitsquareModule;
import io.bitsquare.user.AccountSettings;
import io.bitsquare.arbitration.ArbitratorMessageModule;
import io.bitsquare.arbitration.tomp2p.TomP2PArbitratorMessageModule;
import io.bitsquare.btc.BitcoinModule;
import io.bitsquare.crypto.CryptoModule;
import io.bitsquare.gui.GuiModule;
import io.bitsquare.network.NetworkModule;
import io.bitsquare.network.tomp2p.TomP2PNetworkModule;
import io.bitsquare.offer.OfferModule;
import io.bitsquare.offer.tomp2p.TomP2POfferModule;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.user.Preferences;
import io.bitsquare.trade.TradeMessageModule;
import io.bitsquare.trade.tomp2p.TomP2PTradeMessageModule;
import io.bitsquare.user.User;

import com.google.inject.Injector;
import com.google.inject.Singleton;

import java.io.File;

import javafx.stage.Stage;

import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

class BitsquareAppModule extends BitsquareModule {

    private final Stage primaryStage;

    public BitsquareAppModule(Environment env, Stage primaryStage) {
        super(env);
        this.primaryStage = primaryStage;
    }

    @Override
    protected void configure() {
        bind(User.class).in(Singleton.class);
        bind(Preferences.class).in(Singleton.class);
        bind(AccountSettings.class).in(Singleton.class);

        File persistenceDir = new File(env.getRequiredProperty(Persistence.DIR_KEY));
        bind(File.class).annotatedWith(named(Persistence.DIR_KEY)).toInstance(persistenceDir);
        bindConstant().annotatedWith(named(Persistence.PREFIX_KEY)).to(env.getRequiredProperty(Persistence.PREFIX_KEY));
        bind(Persistence.class).in(Singleton.class);

        bind(Environment.class).toInstance(env);
        bind(UpdateProcess.class).in(Singleton.class);

        install(networkModule());
        install(bitcoinModule());
        install(cryptoModule());
        install(tradeMessageModule());
        install(offerModule());
        install(arbitratorMessageModule());
        install(guiModule());
    }

    protected ArbitratorMessageModule arbitratorMessageModule() {
        return new TomP2PArbitratorMessageModule(env);
    }

    protected NetworkModule networkModule() {
        return new TomP2PNetworkModule(env);
    }

    protected BitcoinModule bitcoinModule() {
        return new BitcoinModule(env);
    }

    protected CryptoModule cryptoModule() {
        return new CryptoModule(env);
    }

    protected TradeMessageModule tradeMessageModule() {
        return new TomP2PTradeMessageModule(env);
    }

    protected OfferModule offerModule() {
        return new TomP2POfferModule(env);
    }

    protected GuiModule guiModule() {
        return new GuiModule(env, primaryStage);
    }

    @Override
    protected void doClose(Injector injector) {
    }
}
