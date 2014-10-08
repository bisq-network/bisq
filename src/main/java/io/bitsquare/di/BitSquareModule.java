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


import io.bitsquare.BitSquare;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.main.trade.orderbook.OrderBook;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BankAccountNumberValidator;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.PasswordValidator;
import io.bitsquare.msg.BootstrappedPeerFactory;
import io.bitsquare.msg.DHTSeedService;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.P2PNode;
import io.bitsquare.msg.SeedNodeAddress;
import io.bitsquare.msg.actor.DHTManager;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;
import io.bitsquare.util.ConfigLoader;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.name.Names;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

public class BitSquareModule extends AbstractModule {
    private static final Logger log = LoggerFactory.getLogger(BitSquareModule.class);

    static Properties properties;

    @Override
    protected void configure() {
        bind(User.class).asEagerSingleton();
        bind(Persistence.class).asEagerSingleton();
        bind(Settings.class).asEagerSingleton();

        bind(CryptoFacade.class).asEagerSingleton();
        bind(WalletFacade.class).asEagerSingleton();
        bind(FeePolicy.class).asEagerSingleton();

        bind(BlockChainFacade.class).asEagerSingleton();
        bind(MessageFacade.class).asEagerSingleton();
        bind(P2PNode.class).asEagerSingleton();
        bind(BootstrappedPeerFactory.class).asEagerSingleton();

        bind(TradeManager.class).asEagerSingleton();
        bind(OrderBook.class).asEagerSingleton();
        bind(Navigation.class).asEagerSingleton();
        bind(OverlayManager.class).asEagerSingleton();
        bind(BSFormatter.class).asEagerSingleton();

        bind(BankAccountNumberValidator.class).asEagerSingleton();
        bind(BtcValidator.class).asEagerSingleton();
        bind(FiatValidator.class).asEagerSingleton();
        bind(InputValidator.class).asEagerSingleton();
        bind(PasswordValidator.class).asEagerSingleton();

        bind(NetworkParameters.class).toProvider(NetworkParametersProvider.class).asEagerSingleton();

        // we will probably later disc storage instead of memory storage for TomP2P
        // bind(Boolean.class).annotatedWith(Names.named("useDiskStorage")).toInstance(true);
        bind(Boolean.class).annotatedWith(Names.named("useDiskStorage")).toInstance(false);

        bind(SeedNodeAddress.StaticSeedNodeAddresses.class).annotatedWith(Names.named("defaultSeedNode"))
                .toProvider(StaticSeedNodeAddressesProvider.class).asEagerSingleton();

        // Actor Related Classes to Inject
        bind(ActorSystem.class).toProvider(ActorSystemProvider.class).asEagerSingleton();

        bind(DHTSeedService.class);
    }
}

class StaticSeedNodeAddressesProvider implements Provider<SeedNodeAddress.StaticSeedNodeAddresses> {
    private static final Logger log = LoggerFactory.getLogger(StaticSeedNodeAddressesProvider.class);

    public SeedNodeAddress.StaticSeedNodeAddresses get() {
        if (BitSquareModule.properties == null)
            BitSquareModule.properties = ConfigLoader.loadConfig();

        log.info("seedNode from config file: " + BitSquareModule.properties.getProperty("defaultSeedNode"));
        String seedNodeFromConfig = BitSquareModule.properties.getProperty("defaultSeedNode");

        // Set default 
        SeedNodeAddress.StaticSeedNodeAddresses seedNode = SeedNodeAddress.StaticSeedNodeAddresses.LOCALHOST;
        // SeedNodeAddress.StaticSeedNodeAddresses seedNode = SeedNodeAddress.StaticSeedNodeAddresses.DIGITAL_OCEAN;

        // if defined in config we override the above
        if (seedNodeFromConfig != null)
            seedNode = seedNodeFromConfig.equals("localhost") ?
                    SeedNodeAddress.StaticSeedNodeAddresses.LOCALHOST :
                    SeedNodeAddress.StaticSeedNodeAddresses.DIGITAL_OCEAN;
        return seedNode;
    }
}

class NetworkParametersProvider implements Provider<NetworkParameters> {
    private static final Logger log = LoggerFactory.getLogger(NetworkParametersProvider.class);

    public NetworkParameters get() {
        NetworkParameters result = null;

        //If config is available we override the networkType defined in Guice with the one from the config file
        if (BitSquareModule.properties == null)
            BitSquareModule.properties = ConfigLoader.loadConfig();

        log.info("networkType from config file: " + BitSquareModule.properties.getProperty("networkType"));
        String networkTypeFromConfig = BitSquareModule.properties.getProperty("networkType");

        // Set default
        // String networkType= WalletFacade.MAIN_NET;
        //String networkType = WalletFacade.TEST_NET;
        String networkType = WalletFacade.REG_TEST_NET;

        if (networkTypeFromConfig != null)
            networkType = networkTypeFromConfig;

        switch (networkType) {
            case WalletFacade.MAIN_NET:
                result = MainNetParams.get();
                break;
            case WalletFacade.TEST_NET:
                result = TestNet3Params.get();
                break;
            case WalletFacade.REG_TEST_NET:
                result = RegTestParams.get();
                break;
        }
        return result;
    }
}

class ActorSystemProvider implements Provider<ActorSystem> {

    @Override
    public ActorSystem get() {
        ActorSystem system = ActorSystem.create(BitSquare.getAppName());

        // create top level actors
        system.actorOf(DHTManager.getProps(), DHTManager.SEED_NAME);

        return system;
    }
}