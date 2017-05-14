package io.bisq.core.proto.persistable;

import com.google.inject.Provider;
import io.bisq.common.locale.*;
import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistableViewPath;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.core.trade.*;
import io.bisq.core.trade.statistics.TradeStatisticsList;
import io.bisq.core.user.BlockChainExplorer;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.UserPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.peers.peerexchange.PeerList;
import io.bisq.network.p2p.storage.SequenceNumberMap;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.HashMap;

@Slf4j
public class CorePersistenceProtoResolver extends CoreProtoResolver implements PersistenceProtoResolver {
    private final Provider<Preferences> preferencesProvider;
    private final Storage<TradableList<OpenOffer>> openOfferStorage;
    private final Storage<TradableList<BuyerAsMakerTrade>> buyerAsMakerTradeStorage;
    private final Storage<TradableList<BuyerAsTakerTrade>> buyerAsTakerTradeStorage;
    private final Storage<TradableList<SellerAsMakerTrade>> sellerAsMakerTradeStorage;
    private final Storage<TradableList<SellerAsTakerTrade>> sellerAsTakerTradeStorage;
    private final Provider<BtcWalletService> btcWalletService;

    @Inject
    public CorePersistenceProtoResolver(Provider<Preferences> preferencesProvider,
                                        Provider<BtcWalletService> btcWalletService,
                                        @Named(Storage.STORAGE_DIR) File storageDir) {
        this.preferencesProvider = preferencesProvider;
        this.btcWalletService = btcWalletService;

        openOfferStorage = new Storage<>(storageDir, this);
        buyerAsMakerTradeStorage = new Storage<>(storageDir, this);
        buyerAsTakerTradeStorage = new Storage<>(storageDir, this);
        sellerAsMakerTradeStorage = new Storage<>(storageDir, this);
        sellerAsTakerTradeStorage = new Storage<>(storageDir, this);
    }

    @Override
    public PersistableEnvelope fromProto(PB.PersistableEnvelope proto) {
        log.error("Convert protobuffer disk proto: {}", proto.getMessageCase());

        switch (proto.getMessageCase()) {
            case ADDRESS_ENTRY_LIST:
                return AddressEntryList.fromProto(proto.getAddressEntryList());
            case VIEW_PATH_AS_STRING:
                return PersistableViewPath.fromProto(proto.getViewPathAsString());
            case OPEN_OFFER_LIST:
                return TradableList.fromProto(proto.getOpenOfferList(), openOfferStorage);
            case TRADABLE_LIST:
                return TradableList.fromProto(proto.getTradableList(),
                        this,
                        openOfferStorage,
                        buyerAsMakerTradeStorage,
                        buyerAsTakerTradeStorage,
                        sellerAsMakerTradeStorage,
                        sellerAsTakerTradeStorage,
                        btcWalletService.get());
            case PEER_LIST:
                return PeerList.fromProto(proto.getPeerList());
            case COMPENSATION_REQUEST_PAYLOAD:
                // TODO There will be another object for PersistableEnvelope
                return CompensationRequestPayload.fromProto(proto.getCompensationRequestPayload());
            case PREFERENCES:
                return fillPreferences(proto, preferencesProvider.get());
            case USER_PAYLOAD:
                return UserPayload.fromProto(proto.getUserPayload(), this);
            case SEQUENCE_NUMBER_MAP:
                return SequenceNumberMap.fromProto(proto.getSequenceNumberMap());
            case TRADE_STATISTICS_LIST:
                return TradeStatisticsList.fromProto(proto.getTradeStatisticsList());
            default:
                throw new ProtobufferException("Unknown proto message case. messageCase=" + proto.getMessageCase());
        }
    }

    // @Override
    public Tradable fromProto(PB.Tradable proto, Storage<TradableList<SellerAsMakerTrade>> storage) {
        log.error("Convert protobuffer disk proto: {}", proto.getMessageCase());

        switch (proto.getMessageCase()) {
            case OPEN_OFFER:
                return OpenOffer.fromProto(proto.getOpenOffer());
            case BUYER_AS_MAKER_TRADE:
                return BuyerAsMakerTrade.fromProto(proto.getBuyerAsMakerTrade(), storage, btcWalletService.get());
            case BUYER_AS_TAKER_TRADE:
                return BuyerAsTakerTrade.fromProto(proto.getBuyerAsTakerTrade(), storage, btcWalletService.get());
            case SELLER_AS_MAKER_TRADE:
                return SellerAsMakerTrade.fromProto(proto.getSellerAsMakerTrade(), storage, btcWalletService.get());
            case SELLER_AS_TAKER_TRADE:
                return SellerAsTakerTrade.fromProto(proto.getSellerAsTakerTrade(), storage, btcWalletService.get());
            default:
                throw new ProtobufferException("Unknown proto message case. messageCase=" + proto.getMessageCase());
        }
    }


    private Preferences fillPreferences(PB.PersistableEnvelope envelope, Preferences preferences) {
        final PB.Preferences env = envelope.getPreferences();
        preferences.setUserLanguage(env.getUserLanguage());
        PB.Country userCountry = env.getUserCountry();
        preferences.setUserCountry(new Country(userCountry.getCode(), userCountry.getName(), new Region(userCountry.getRegion().getCode(), userCountry.getRegion().getName())));
        env.getFiatCurrenciesList().stream()
                .forEach(tradeCurrency -> preferences.addFiatCurrency((FiatCurrency) TradeCurrency.fromProto(tradeCurrency)));
        env.getCryptoCurrenciesList().stream()
                .forEach(tradeCurrency -> preferences.addCryptoCurrency((CryptoCurrency) TradeCurrency.fromProto(tradeCurrency)));
        PB.BlockChainExplorer bceMain = env.getBlockChainExplorerMainNet();
        preferences.setBlockChainExplorerMainNet(new BlockChainExplorer(bceMain.getName(), bceMain.getTxUrl(), bceMain.getAddressUrl()));
        PB.BlockChainExplorer bceTest = env.getBlockChainExplorerTestNet();
        preferences.setBlockChainExplorerTestNet(new BlockChainExplorer(bceTest.getName(), bceTest.getTxUrl(), bceTest.getAddressUrl()));

        preferences.setAutoSelectArbitrators(env.getAutoSelectArbitrators());
        preferences.setDontShowAgainMap(new HashMap<>(env.getDontShowAgainMapMap()));
        preferences.setTacAccepted(env.getTacAccepted());
        preferences.setUseTorForBitcoinJ(env.getUseTorForBitcoinJ());
        preferences.setShowOwnOffersInOfferBook(env.getShowOwnOffersInOfferBook());
        PB.TradeCurrency preferredTradeCurrency = env.getPreferredTradeCurrency();
        preferences.setPreferredTradeCurrency(TradeCurrency.fromProto(preferredTradeCurrency));
        preferences.setWithdrawalTxFeeInBytes(env.getWithdrawalTxFeeInBytes());
        preferences.setMaxPriceDistanceInPercent(env.getMaxPriceDistanceInPercent());

        preferences.setSortMarketCurrenciesNumerically(env.getSortMarketCurrenciesNumerically());
        preferences.setUsePercentageBasedPrice(env.getUsePercentageBasedPrice());
        preferences.setPeerTagMap(env.getPeerTagMapMap());
        preferences.setBitcoinNodes(env.getBitcoinNodes());
        preferences.setIgnoreTradersList(env.getIgnoreTradersListList());
        preferences.setDirectoryChooserPath(env.getDirectoryChooserPath());
        preferences.setBuyerSecurityDepositAsLong(env.getBuyerSecurityDepositAsLong());

        final PB.BlockChainExplorer bsqExPl = env.getBsqBlockChainExplorer();
        preferences.setBsqBlockChainExplorer(new BlockChainExplorer(bsqExPl.getName(), bsqExPl.getTxUrl(), bsqExPl.getAddressUrl()));

        preferences.setBtcDenomination(env.getBtcDenomination());
        preferences.setUseAnimations(env.getUseAnimations());
        preferences.setPayFeeInBtc(env.getPayFeeInBtc());
        preferences.setResyncSpvRequested(env.getResyncSpvRequested());

        // optional
        preferences.setBackupDirectory(env.getBackupDirectory().isEmpty() ? null : env.getBackupDirectory());
        preferences.setOfferBookChartScreenCurrencyCode(env.getOfferBookChartScreenCurrencyCode().isEmpty() ?
                null :
                env.getOfferBookChartScreenCurrencyCode());
        preferences.setTradeChartsScreenCurrencyCode(env.getTradeChartsScreenCurrencyCode().isEmpty() ?
                null :
                env.getTradeChartsScreenCurrencyCode());
        preferences.setBuyScreenCurrencyCode(env.getBuyScreenCurrencyCode().isEmpty() ? null : env.getBuyScreenCurrencyCode());
        preferences.setSellScreenCurrencyCode(env.getSellScreenCurrencyCode().isEmpty() ? null : env.getSellScreenCurrencyCode());
        preferences.setSelectedPaymentAccountForCreateOffer(env.getSelectedPaymentAccountForCreateOffer().hasPaymentMethod() ?
                PaymentAccount.fromProto(env.getSelectedPaymentAccountForCreateOffer(), this) :
                null);

        preferences.setDoPersist(true);
        return preferences;
    }
}
