package io.bisq.core.proto.persistable;

import com.google.inject.Provider;
import io.bisq.common.locale.*;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistableList;
import io.bisq.common.proto.persistable.PersistableViewPath;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.core.trade.*;
import io.bisq.core.trade.statistics.TradeStatistics;
import io.bisq.core.user.BlockChainExplorer;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.UserPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import io.bisq.network.p2p.storage.SequenceNumberMap;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class CorePersistenceProtoResolver extends CoreProtoResolver implements PersistenceProtoResolver {
    private final Provider<AddressEntryList> addressEntryListProvider;
    private final Provider<Preferences> preferencesProvider;
    private final Storage<TradableList<OpenOffer>> openOfferStorage;
    private final Storage<TradableList<BuyerAsMakerTrade>> buyerAsMakerTradeStorage;
    private final Storage<TradableList<BuyerAsTakerTrade>> buyerAsTakerTradeStorage;
    private final Storage<TradableList<SellerAsMakerTrade>> sellerAsMakerTradeStorage;
    private final Storage<TradableList<SellerAsTakerTrade>> sellerAsTakerTradeStorage;
    private final Provider<BtcWalletService> btcWalletService;

    @Inject
    public CorePersistenceProtoResolver(Provider<Preferences> preferencesProvider,
                                        Provider<AddressEntryList> addressEntryListProvider,
                                        Provider<BtcWalletService> btcWalletService,
                                        @Named(Storage.STORAGE_DIR) File storageDir) {
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
    public Optional<PersistableEnvelope> fromProto(PB.PersistableEnvelope proto) {
        if (Objects.isNull(proto)) {
            log.warn("fromProtoBuf called with empty disk proto.");
            return Optional.empty();
        }

        log.debug("Convert protobuffer disk proto: {}", proto.getMessageCase());

        PersistableEnvelope result = null;
        switch (proto.getMessageCase()) {
            case ADDRESS_ENTRY_LIST:
                result = fillAddressEntryList(proto, addressEntryListProvider.get());
                break;
            case VIEW_PATH_AS_STRING:
                result = PersistableViewPath.fromProto(proto.getViewPathAsString());
                break;
            case TRADABLE_LIST:
                result = getTradableList(proto.getTradableList());
                break;
            case PEERS_LIST:
                result = getPeersList(proto.getPeersList());
                break;
            case COMPENSATION_REQUEST_PAYLOAD:
                // TODO There will be another object for PersistableEnvelope
                result = CompensationRequestPayload.fromProto(proto.getCompensationRequestPayload());
                break;
            case PREFERENCES:
                result = fillPreferences(proto, preferencesProvider.get());
                break;
            case USER_PAYLOAD:
                result = UserPayload.fromProto(proto.getUserPayload(), this);
                break;
            case SEQUENCE_NUMBER_MAP:
                result = SequenceNumberMap.fromProto(proto.getSequenceNumberMap());
                break;
            case TRADE_STATISTICS_LIST:
                result = getTradeStatisticsList(proto.getTradeStatisticsList());
            default:
                log.warn("Unknown message case:{}:{}", proto.getMessageCase());
        }
        return Optional.ofNullable(result);
    }

/*

    @NotNull
    static OKPayAccountPayload getOkPayAccountPayload(PB.PaymentAccountPayload protoEntry) {
        OKPayAccountPayload okPayAccountPayload = new OKPayAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                protoEntry.getMaxTradePeriod(), protoEntry.getOKPayAccountPayload().getAccountNr());
        okPayAccountPayload.setAccountNr(protoEntry.getOKPayAccountPayload().getAccountNr());
        return okPayAccountPayload;
    }
*/


    private PersistableEnvelope getTradableList(PB.TradableList tradableList) {
        return TradableList.fromProto(tradableList, openOfferStorage, buyerAsMakerTradeStorage, buyerAsTakerTradeStorage, sellerAsMakerTradeStorage, sellerAsTakerTradeStorage, btcWalletService.get());
    }

    private PersistableEnvelope getTradeStatisticsList(PB.TradeStatisticsList tradeStatisticsList) {
        return new PersistableList<>(tradeStatisticsList.getTradeStatisticsList().stream()
                .map(TradeStatistics::fromProto).collect(Collectors.toList()));
    }

    private PersistableEnvelope getPeersList(PB.PeersList envelope) {
        return new PersistableList<>(envelope.getPeersList().stream().map(Peer::fromProto)
                .collect(Collectors.toList()));
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

    private AddressEntryList fillAddressEntryList(PB.PersistableEnvelope envelope, AddressEntryList addressEntryList) {
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
