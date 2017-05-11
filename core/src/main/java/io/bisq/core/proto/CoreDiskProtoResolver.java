package io.bisq.core.proto;

import com.google.inject.Provider;
import io.bisq.common.locale.*;
import io.bisq.common.persistence.ListPersistable;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.proto.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.trade.*;
import io.bisq.core.trade.statistics.TradeStatistics;
import io.bisq.core.user.BlockChainExplorer;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import io.bisq.network.p2p.storage.SequenceNumberMap;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * If the Messages class is giving errors in IntelliJ, you should change the IntelliJ IDEA Platform Properties file,
 * idea.properties, to something bigger like 12500:
 * <p>
 * #---------------------------------------------------------------------
 * # Maximum file size (kilobytes) IDE should provide code assistance for.
 * # The larger file is the slower its editor works and higher overall system memory requirements are
 * # if code assistance is enabled. Remove this property or set to very large number if you need
 * # code assistance for any files available regardless their size.
 * #---------------------------------------------------------------------
 * idea.max.intellisense.filesize=2500
 */
@Slf4j
public class CoreDiskProtoResolver implements PersistenceProtoResolver {

    private Provider<AddressEntryList> addressEntryListProvider;
    private Provider<Preferences> preferencesProvider;

    private Storage<TradableList<OpenOffer>> openOfferStorage;
    private Storage<TradableList<BuyerAsMakerTrade>> buyerAsMakerTradeStorage;
    private Storage<TradableList<BuyerAsTakerTrade>> buyerAsTakerTradeStorage;
    private Storage<TradableList<SellerAsMakerTrade>> sellerAsMakerTradeStorage;
    private Storage<TradableList<SellerAsTakerTrade>> sellerAsTakerTradeStorage;
    private Provider<BtcWalletService> btcWalletService;

    @Inject
    public CoreDiskProtoResolver(Provider<Preferences> preferencesProvider,
                                 Provider<AddressEntryList> addressEntryListProvider,
                                 Provider<BtcWalletService> btcWalletService,
                                 @Named(Storage.STORAGE_DIR) File storageDir
    ) {
        this.preferencesProvider = preferencesProvider;
        this.addressEntryListProvider = addressEntryListProvider;
        this.btcWalletService = btcWalletService;

        openOfferStorage = new Storage<>(storageDir, this);
        buyerAsMakerTradeStorage = new Storage<>(storageDir, this);
        buyerAsTakerTradeStorage = new Storage<>(storageDir, this);
        sellerAsMakerTradeStorage = new Storage<>(storageDir, this);
        sellerAsTakerTradeStorage = new Storage<>(storageDir, this);
    }

    @Override
    public Optional<Persistable> fromProto(PB.Persistable envelope) {
        if (Objects.isNull(envelope)) {
            log.warn("fromProtoBuf called with empty disk envelope.");
            return Optional.empty();
        }

        log.debug("Convert protobuffer disk envelope: {}", envelope.getMessageCase());

        Persistable result = null;
        switch (envelope.getMessageCase()) {
            case ADDRESS_ENTRY_LIST:
                result = fillAddressEntryList(envelope, addressEntryListProvider.get());
                break;
            case VIEW_PATH_AS_STRING:
                result = ViewPathAsString.fromProto(envelope.getViewPathAsString());
                break;
            case TRADABLE_LIST:
                result = getTradableList(envelope.getTradableList());
                break;
            case PEERS_LIST:
                result = getPeersList(envelope.getPeersList());
                break;
            case COMPENSATION_REQUEST_PAYLOAD:
                result = CompensationRequestPayload.fromProto(envelope.getCompensationRequestPayload());
                break;
            case PREFERENCES:
                result = fillPreferences(envelope, preferencesProvider.get());
                break;
            case USER:
                result = User.fromProto(envelope.getUser());
                break;
            case SEQUENCE_NUMBER_MAP:
                result = SequenceNumberMap.fromProto(envelope.getSequenceNumberMap());
                break;
            case TRADE_STATISTICS_LIST:
                result = getTradeStatisticsList(envelope.getTradeStatisticsList());
            default:
                log.warn("Unknown message case:{}:{}", envelope.getMessageCase());
        }
        return Optional.ofNullable(result);
    }

    private Persistable getTradableList(PB.TradableList tradableList) {
        return TradableList.fromProto(tradableList, openOfferStorage, buyerAsMakerTradeStorage, buyerAsTakerTradeStorage, sellerAsMakerTradeStorage, sellerAsTakerTradeStorage, btcWalletService.get());
    }

    private Persistable getTradeStatisticsList(PB.TradeStatisticsList tradeStatisticsList) {
        return new ListPersistable<>(tradeStatisticsList.getTradeStatisticsList().stream()
                .map(TradeStatistics::fromProto).collect(Collectors.toList()));
    }

    private Persistable getPeersList(PB.PeersList envelope) {
        return new ListPersistable<>(envelope.getPeersList().stream().map(Peer::fromProto)
                .collect(Collectors.toList()));
    }

    private Preferences fillPreferences(PB.Persistable envelope, Preferences preferences) {
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
        preferences.setOfferBookChartScreenCurrencyCode(env.getOfferBookChartScreenCurrencyCode().isEmpty() ? null : env.getOfferBookChartScreenCurrencyCode());
        preferences.setTradeChartsScreenCurrencyCode(env.getTradeChartsScreenCurrencyCode().isEmpty() ? null : env.getTradeChartsScreenCurrencyCode());
        preferences.setBuyScreenCurrencyCode(env.getBuyScreenCurrencyCode().isEmpty() ? null : env.getBuyScreenCurrencyCode());
        preferences.setSellScreenCurrencyCode(env.getSellScreenCurrencyCode().isEmpty() ? null : env.getSellScreenCurrencyCode());
        preferences.setSelectedPaymentAccountForCreateOffer(env.getSelectedPaymentAccountForCreateOffer().hasPaymentMethod() ? PaymentAccount.fromProto(env.getSelectedPaymentAccountForCreateOffer()) : null);

        preferences.setDoPersist(true);
        return preferences;
    }

    private Locale getLocale(PB.Locale locale) {
        return new Locale(locale.getLanguage(), locale.getCountry(), locale.getVariant());
    }

    private AddressEntryList fillAddressEntryList(PB.Persistable envelope, AddressEntryList addressEntryList) {
        envelope.getAddressEntryList().getAddressEntryList().stream().forEach(addressEntry -> {
            final AddressEntry entry = new AddressEntry(addressEntry.getPubKey().toByteArray(),
                    addressEntry.getPubKeyHash().toByteArray(),
                    AddressEntry.Context.valueOf(addressEntry.getContext().name()),
                    addressEntry.getOfferId(),
                    Coin.valueOf(addressEntry.getCoinLockedInMultiSig()));
            addressEntryList.addAddressEntry(entry);
        });
        addressEntryList.setDoPersist(true);
        return addressEntryList;
    }
}
