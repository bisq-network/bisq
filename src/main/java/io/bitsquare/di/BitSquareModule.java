package io.bitsquare.di;


import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.RegTestParams;
import com.google.bitcoin.params.TestNet3Params;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.SeedNodeAddress;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Persistence;
import io.bitsquare.trade.Trading;
import io.bitsquare.trade.orderbook.OrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import javax.inject.Inject;

public class BitSquareModule extends AbstractModule
{

    @Override
    protected void configure()
    {
        bind(User.class).asEagerSingleton();
        bind(OrderBook.class).asEagerSingleton();
        bind(Persistence.class).asEagerSingleton();
        bind(Settings.class).asEagerSingleton();
        bind(OrderBookFilter.class).asEagerSingleton();

        bind(CryptoFacade.class).asEagerSingleton();
        bind(WalletFacade.class).asEagerSingleton();
        bind(FeePolicy.class).asEagerSingleton();

        bind(BlockChainFacade.class).asEagerSingleton();
        bind(MessageFacade.class).asEagerSingleton();

        bind(Trading.class).asEagerSingleton();
        bind(BitSquareFormatter.class).asEagerSingleton();


        //bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.MAIN_NET);
        // how to use reg test see description in the readme file
        bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.REG_TEST_NET);
        //test net not working yet: http://sourceforge.net/p/bitcoin/mailman/message/32349208/
        //bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.TEST_NET);
        bind(NetworkParameters.class).toProvider(NetworkParametersProvider.class).asEagerSingleton();

        // bind(Boolean.class).annotatedWith(Names.named("useDiskStorage")).toInstance(new Boolean(true));
        bind(Boolean.class).annotatedWith(Names.named("useDiskStorage")).toInstance(new Boolean(false));
        bind(SeedNodeAddress.StaticSeedNodeAddresses.class).annotatedWith(Names.named("defaultSeedNode")).toInstance(SeedNodeAddress.StaticSeedNodeAddresses.LOCALHOST);
        // bind(SeedNodeAddress.StaticSeedNodeAddresses.class).annotatedWith(Names.named("defaultSeedNode")).toInstance(SeedNodeAddress.StaticSeedNodeAddresses.DIGITAL_OCEAN);
    }
}

class NetworkParametersProvider implements Provider<NetworkParameters>
{
    private final String networkType;

    @Inject
    public NetworkParametersProvider(@Named("networkType") String networkType)
    {
        this.networkType = networkType;
    }


    public NetworkParameters get()
    {
        NetworkParameters result = null;

        switch (networkType)
        {
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
