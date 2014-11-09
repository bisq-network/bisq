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

import com.google.inject.Injector;
import com.google.inject.name.Names;

import java.util.Properties;

import net.tomp2p.connection.Ports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import net.sourceforge.argparse4j.inf.Namespace;
import scala.concurrent.duration.Duration;

/**
 * Configures all non-UI modules necessary to run a Bitsquare application.
 */
public class AppModule extends BitsquareModule {
    private static final Logger log = LoggerFactory.getLogger(AppModule.class);

    private Namespace argumentsNamespace;
    private final String appName;

    public AppModule(Properties properties, Namespace argumentsNamespace, String appName) {
        super(properties);
        this.argumentsNamespace = argumentsNamespace;
        this.appName = appName;
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

        bindConstant().annotatedWith(Names.named("appName")).to(appName);
        bind(ActorSystem.class).toInstance(ActorSystem.create(appName));

        int randomPort = new Ports().tcpPort();
        bindConstant().annotatedWith(Names.named("clientPort")).to(randomPort);
    }

    protected MessageModule messageModule() {
        return new TomP2PMessageModule(properties, argumentsNamespace);
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
        ActorSystem actorSystem = injector.getInstance(ActorSystem.class);
        actorSystem.shutdown();
        try {
            actorSystem.awaitTermination(Duration.create(5L, "seconds"));
        } catch (Exception ex) {
            log.error("Actor system failed to shut down properly", ex);
        }
    }
}

