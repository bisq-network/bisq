package io.bitsquare.btc;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.di.BitSquareModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class WalletFacadeTest
{

    private WalletFacade walletFacade;

    @Before
    public void setUp()
    {
        final Injector injector = Guice.createInjector(new BitSquareModule());
        walletFacade = injector.getInstance(WalletFacade.class);
        walletFacade.initWallet();

        //TODO
          /*

java.lang.IllegalStateException: Toolkit not initialized
	at com.sun.javafx.application.PlatformImpl.runLater(PlatformImpl.java:276)
	at com.sun.javafx.application.PlatformImpl.runLater(PlatformImpl.java:271)
	at javafx.application.Platform.runLater(Platform.java:78)
	at io.bitsquare.btc.WalletFacade$$Lambda$1/833474933.execute(Unknown Source)
	at com.google.bitcoin.core.Wallet.queueOnKeysAdded(Wallet.java:3301)
	at com.google.bitcoin.core.Wallet.addKeys(Wallet.java:2024)
	at com.google.bitcoin.core.Wallet.addKey(Wallet.java:1996)
	at io.bitsquare.btc.WalletFacade.getNewAddressInfo(WalletFacade.java:383)
           */
    }

    @After
    public void tearDown()
    {
        //walletFacade.shutDown();
    }

    @Test
    public void testStringToDouble()
    {
        // AddressEntry addressEntry = walletFacade.getUnusedTradeAddressInfo();
        // assertFalse("no tx", walletFacade.isUnusedTradeAddressBalanceAboveCreationFee());

       /* Transaction tx = new Transaction(walletFacade.getWallet().getNetworkParameters());
        WalletTransaction walletTransaction = new WalletTransaction(WalletTransaction.Pool.PENDING, tx);
        walletFacade.getWallet().addWalletTransaction(walletTransaction);

        assertFalse("tx unfunded, pending", walletFacade.isUnusedTradeAddressBalanceAboveCreationFee());   */

    }
}
