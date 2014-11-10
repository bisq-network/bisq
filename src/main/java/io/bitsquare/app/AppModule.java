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
import io.bitsquare.btc.BitcoinModule;
import io.bitsquare.crypto.CryptoModule;
import io.bitsquare.msg.MessageModule;
import io.bitsquare.msg.tomp2p.TomP2PMessageModule;
import io.bitsquare.offer.OfferModule;
import io.bitsquare.offer.tomp2p.TomP2POfferModule;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.TradeModule;
import io.bitsquare.user.User;

import com.google.common.base.Preconditions;

import com.google.inject.Injector;
import com.google.inject.name.Names;

import java.util.Properties;

import net.tomp2p.connection.Ports;

/**
 * Configures all non-UI modules necessary to run a Bitsquare application.
 */
public class AppModule extends BitsquareModule {
    public static final String APP_NAME_KEY = "appName";

    public AppModule(Properties properties) {
        super(properties);
    }

    @Override
    protected void configure() {
        bind(User.class).asEagerSingleton();
        bind(Persistence.class).asEagerSingleton();
        bind(Settings.class).asEagerSingleton();

        install(messageModule());
        install(bitcoinModule());
        install(cryptoModule());
        install(tradeModule());
        install(offerModule());

        String appName = properties.getProperty(APP_NAME_KEY);
        Preconditions.checkArgument(appName != null, "App name must be non-null");

        bindConstant().annotatedWith(Names.named("appName")).to(appName);

        int randomPort = new Ports().tcpPort();
        bindConstant().annotatedWith(Names.named("clientPort")).to(randomPort);
    }

    protected MessageModule messageModule() {
        return new TomP2PMessageModule(properties);
    }

    protected BitcoinModule bitcoinModule() {
        return new BitcoinModule(properties);
    }

    protected CryptoModule cryptoModule() {
        return new CryptoModule(properties);
    }

    protected TradeModule tradeModule() {
        return new TradeModule(properties);
    }

    protected OfferModule offerModule() {
        return new TomP2POfferModule(properties);
    }

    @Override
    protected void doClose(Injector injector) {
    }
}

