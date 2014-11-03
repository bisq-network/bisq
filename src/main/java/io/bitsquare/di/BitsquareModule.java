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

package io.bitsquare.di;

import io.bitsquare.Bitsquare;
import io.bitsquare.btc.BitcoinModule;
import io.bitsquare.crypto.CryptoModule;
import io.bitsquare.gui.GuiModule;
import io.bitsquare.msg.DefaultMessageModule;
import io.bitsquare.msg.MessageModule;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.TradeModule;
import io.bitsquare.user.User;
import io.bitsquare.util.ConfigLoader;

import com.google.inject.Injector;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import scala.concurrent.duration.Duration;

public class BitsquareModule extends AbstractBitsquareModule {

    private static final Logger log = LoggerFactory.getLogger(BitsquareModule.class);

    public BitsquareModule() {
        this(ConfigLoader.loadConfig());
    }

    public BitsquareModule(Properties properties) {
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
        install(guiModule());

        bind(ActorSystem.class).toInstance(ActorSystem.create(Bitsquare.getAppName()));
    }

    protected MessageModule messageModule() {
        return new DefaultMessageModule(properties);
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

    protected GuiModule guiModule() {
        return new GuiModule(properties);
    }

    @Override
    protected void doClose(Injector injector) {
        ActorSystem actorSystem = injector.getInstance(ActorSystem.class);
        actorSystem.shutdown();
        try {
            actorSystem.awaitTermination(Duration.create(5L, "seconds"));
        } catch (Exception ex) {
            log.error("Actor system failed to shut down properly", ex);
        }
    }
}

