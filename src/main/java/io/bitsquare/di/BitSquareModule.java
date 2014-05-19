package io.bitsquare.di;


import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.RegTestParams;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.Trading;
import io.bitsquare.trade.orderbook.OrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import io.bitsquare.util.Utilities;
import org.spongycastle.util.encoders.Hex;

import java.io.File;

import static com.google.common.base.Preconditions.checkState;

public class BitSquareModule extends AbstractModule
{

    @Override
    protected void configure()
    {
        bind(User.class).asEagerSingleton();
        bind(OrderBook.class).asEagerSingleton();
        bind(Storage.class).asEagerSingleton();
        bind(Settings.class).asEagerSingleton();
        bind(OrderBookFilter.class).asEagerSingleton();

        bind(CryptoFacade.class).asEagerSingleton();
        bind(WalletFacade.class).asEagerSingleton();
        bind(BlockChainFacade.class).asEagerSingleton();
        bind(MessageFacade.class).asEagerSingleton();

        bind(Trading.class).asEagerSingleton();

        // bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.MAIN_NET);
        bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.REG_TEST_NET);
        // bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.TEST_NET);
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
        return new WalletAppKit(networkParameters, new File(Utilities.getRootDir()), WalletFacade.WALLET_PREFIX);
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
            case WalletFacade.MAIN_NET:
                result = MainNetParams.get();
                break;
            case WalletFacade.TEST_NET:
                result = TestNet3Params2.get();
                break;
            case WalletFacade.REG_TEST_NET:
                result = RegTestParams.get();
                break;
        }
        return result;
    }
}


/**
 * UnknownHostException with testnet-seed.bitcoin.petertodd.org so use testnet-seed.bluematt.me as primary DND seed server
 * testnet-seed.bluematt.me delivers 1 dead node, so nothing works yet... ;-(
 * http://sourceforge.net/p/bitcoin/mailman/message/32349208/
 */
class TestNet3Params2 extends NetworkParameters
{
    public TestNet3Params2()
    {
        super();
        id = ID_TESTNET;
        // Genesis hash is 000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943
        packetMagic = 0x0b110907;
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        proofOfWorkLimit = Utils.decodeCompactBits(0x1d00ffffL);
        port = 18333;
        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1296688602L);
        genesisBlock.setDifficultyTarget(0x1d00ffffL);
        genesisBlock.setNonce(414098458);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = 210000;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"));
        alertSigningKey = Hex.decode("04302390343f91cc401d56d68b123028bf52e5fca1939df127f63c6467cdf9c8e2c14b61104cf817d0b780da337893ecc4aaff1309e536162dabbdb45200ca2b0a");

        dnsSeeds = new String[]{
                "testnet-seed.bluematt.me"
        };
    }

    private static TestNet3Params2 instance;

    public static synchronized TestNet3Params2 get()
    {
        if (instance == null)
        {
            instance = new TestNet3Params2();
        }
        return instance;
    }

    public String getPaymentProtocolId()
    {
        return PAYMENT_PROTOCOL_ID_TESTNET;
    }
}
