package io.bitsquare.di;


import com.google.inject.AbstractModule;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.IWalletFacade;
import io.bitsquare.btc.MockWalletFacade;
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
        bind(IWalletFacade.class).to(MockWalletFacade.class).asEagerSingleton();
        bind(BlockChainFacade.class).asEagerSingleton();
        bind(IMessageFacade.class).to(MessageFacade.class).asEagerSingleton();

        bind(TradingFacade.class).asEagerSingleton();
    }
}