package bisq.httpapi;

import bisq.core.btc.BitcoinNodes;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;

import bisq.httpapi.exceptions.UnauthorizedException;
import bisq.httpapi.exceptions.WalletNotReadyException;
import bisq.httpapi.facade.WalletFacade;
import bisq.httpapi.model.AuthResult;
import bisq.httpapi.model.BitcoinNetworkStatus;
import bisq.httpapi.model.P2PNetworkConnection;
import bisq.httpapi.model.P2PNetworkStatus;
import bisq.httpapi.model.PriceFeed;
import bisq.httpapi.model.VersionDetails;
import bisq.httpapi.service.auth.TokenRegistry;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.Statistic;

import bisq.common.app.Version;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Peer;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static java.util.stream.Collectors.toList;

//TODO @bernard we need ot break that apart to smaller domain specific chunks (or then use core domains directly).
// its very hard atm to get an overview here

/**
 * This class is a proxy for all Bisq features the model will use.
 * <p>
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 * => this should be the common gateway to bisq used by all outward-facing API classes.
 * <p>
 * If the bisq code is refactored correctly, this class could become very light.
 */
@Slf4j
public class BisqProxy {
    private final BtcWalletService btcWalletService;
    private final P2PService p2PService;
    private final bisq.core.user.Preferences preferences;
    private final WalletsSetup walletsSetup;
    private final TokenRegistry tokenRegistry;
    private final WalletsManager walletsManager;
    private final PriceFeedService priceFeedService;
    private final WalletFacade walletFacade;

    @Inject
    public BisqProxy(BtcWalletService btcWalletService,
                     P2PService p2PService,
                     bisq.core.user.Preferences preferences,
                     WalletsSetup walletsSetup,
                     TokenRegistry tokenRegistry,
                     WalletsManager walletsManager,
                     PriceFeedService priceFeedService,
                     WalletFacade walletFacade) {
        this.btcWalletService = btcWalletService;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.walletsSetup = walletsSetup;
        this.tokenRegistry = tokenRegistry;
        this.walletsManager = walletsManager;
        this.priceFeedService = priceFeedService;
        this.walletFacade = walletFacade;
    }


    /// START TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////


    /// STOP TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////

    public P2PNetworkStatus getP2PNetworkStatus() {
        final P2PNetworkStatus p2PNetworkStatus = new P2PNetworkStatus();
        final NodeAddress address = p2PService.getAddress();
        if (null != address)
            p2PNetworkStatus.address = address.getFullAddress();
        p2PNetworkStatus.p2pNetworkConnection = p2PService.getNetworkNode().getAllConnections().stream()
                .map(P2PNetworkConnection::new)
                .collect(Collectors.toList());
        p2PNetworkStatus.totalSentBytes = Statistic.totalSentBytesProperty().get();
        p2PNetworkStatus.totalReceivedBytes = Statistic.totalReceivedBytesProperty().get();
        return p2PNetworkStatus;
    }

    public BitcoinNetworkStatus getBitcoinNetworkStatus() {
        final BitcoinNetworkStatus networkStatus = new BitcoinNetworkStatus();
        final List<Peer> peers = walletsSetup.connectedPeersProperty().get();
        if (null != peers)
            networkStatus.peers = peers.stream().map(peer -> peer.getAddress().toString()).collect(Collectors.toList());
        else
            networkStatus.peers = Collections.emptyList();
        networkStatus.useTorForBitcoinJ = preferences.getUseTorForBitcoinJ();
        networkStatus.bitcoinNodesOption = BitcoinNodes.BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()];
        networkStatus.bitcoinNodes = preferences.getBitcoinNodes();
        return networkStatus;
    }

    public VersionDetails getVersionDetails() {
        final VersionDetails versionDetails = new VersionDetails();
        versionDetails.application = Version.VERSION;
        versionDetails.network = Version.P2P_NETWORK_VERSION;
        versionDetails.p2PMessage = Version.getP2PMessageVersion();
        versionDetails.localDB = Version.LOCAL_DB_VERSION;
        versionDetails.tradeProtocol = Version.TRADE_PROTOCOL_VERSION;
        return versionDetails;
    }

    public AuthResult authenticate(String password) {
        final boolean isPasswordValid = btcWalletService.isWalletReady() && btcWalletService.isEncrypted() && walletFacade.isWalletPasswordValid(password);
        if (isPasswordValid) {
            return new AuthResult(tokenRegistry.generateToken());
        }
        throw new UnauthorizedException();
    }

    public AuthResult changePassword(String oldPassword, String newPassword) {
        if (!btcWalletService.isWalletReady())
            throw new WalletNotReadyException("Wallet not ready yet");
        if (btcWalletService.isEncrypted()) {
            final KeyParameter aesKey = null == oldPassword ? null : walletFacade.getAESKey(oldPassword);
            if (!walletFacade.isWalletPasswordValid(aesKey))
                throw new UnauthorizedException();
            walletsManager.decryptWallets(aesKey);
        }
        if (null != newPassword && newPassword.length() > 0) {
            final Tuple2<KeyParameter, KeyCrypterScrypt> aesKeyAndScrypt = walletFacade.getAESKeyAndScrypt(newPassword);
            walletsManager.encryptWallets(aesKeyAndScrypt.second, aesKeyAndScrypt.first);
            tokenRegistry.clear();
            return new AuthResult(tokenRegistry.generateToken());
        }
        return null;
    }

    public PriceFeed getPriceFeed(String[] codes) {
        final List<FiatCurrency> fiatCurrencies = preferences.getFiatCurrencies();
        final List<CryptoCurrency> cryptoCurrencies = preferences.getCryptoCurrencies();
        final Stream<String> codesStream;
        if (null == codes || 0 == codes.length)
            codesStream = Stream.concat(fiatCurrencies.stream(), cryptoCurrencies.stream()).map(TradeCurrency::getCode);
        else
            codesStream = Arrays.asList(codes).stream();
        final List<MarketPrice> marketPrices = codesStream
                .map(priceFeedService::getMarketPrice)
                .filter(i -> null != i)
                .collect(toList());
        final PriceFeed priceFeed = new PriceFeed();
        for (MarketPrice price : marketPrices)
            priceFeed.prices.put(price.getCurrencyCode(), price.getPrice());
        return priceFeed;
    }
}
