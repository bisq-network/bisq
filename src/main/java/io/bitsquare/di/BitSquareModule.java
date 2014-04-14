package io.bitsquare.di;


import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.RegTestParams;
import com.google.bitcoin.params.TestNet3Params;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.IWalletFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.ICryptoFacade;
import io.bitsquare.crypto.MockCryptoFacade;
import io.bitsquare.msg.IMessageFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.OrderBookFilterSettings;
import io.bitsquare.settings.Settings;
import io.bitsquare.setup.ISetup;
import io.bitsquare.setup.MockSetup;
import io.bitsquare.storage.IStorage;
import io.bitsquare.storage.SimpleStorage;
import io.bitsquare.trade.TradingFacade;
import io.bitsquare.trade.orderbook.IOrderBook;
import io.bitsquare.trade.orderbook.MockOrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;

import java.io.File;

public class BitSquareModule extends AbstractModule
{


    @Override
    protected void configure()
    {
        bind(ISetup.class).to(MockSetup.class).asEagerSingleton();
        bind(User.class).asEagerSingleton();
        bind(IOrderBook.class).to(MockOrderBook.class).asEagerSingleton();
        bind(IStorage.class).to(SimpleStorage.class).asEagerSingleton();
        bind(Settings.class).asEagerSingleton();
        bind(OrderBookFilter.class).asEagerSingleton();
        bind(OrderBookFilterSettings.class).asEagerSingleton();

        bind(ICryptoFacade.class).to(MockCryptoFacade.class).asEagerSingleton();
        bind(IWalletFacade.class).to(WalletFacade.class).asEagerSingleton();
        bind(BlockChainFacade.class).asEagerSingleton();
        bind(IMessageFacade.class).to(MessageFacade.class).asEagerSingleton();

        bind(TradingFacade.class).asEagerSingleton();

        bind(String.class).annotatedWith(Names.named("networkType")).toInstance(IWalletFacade.MAIN_NET);
        bind(NetworkParameters.class).toProvider(NetworkParametersProvider.class).asEagerSingleton();
        bind(WalletAppKit.class).toProvider(WalletAppKitProvider.class).asEagerSingleton();
    }


}

class WalletAppKitProvider implements Provider<WalletAppKit>
{
    private NetworkParameters networkParameters;

    @Inject
    public WalletAppKitProvider(NetworkParameters networkParameters)
    {
        this.networkParameters = networkParameters;
    }

    public WalletAppKit get()
    {
        return new WalletAppKit(networkParameters, new File("."), "bitsquare");
    }
}

class NetworkParametersProvider implements Provider<NetworkParameters>
{
    private String networkType;

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
            case IWalletFacade.MAIN_NET:
                result = MainNetParams.get();
                break;
            case IWalletFacade.TEST_NET:
                result = TestNet3Params.get();
                break;
            case IWalletFacade.REG_TEST_NET:
                result = RegTestParams.get();
                break;
        }
        return result;
    }
}