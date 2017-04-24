package io.bisq.core.proto;

import com.google.inject.Provider;
import io.bisq.common.crypto.Hash;
import io.bisq.common.locale.*;
import io.bisq.common.persistence.HashMapPersistable;
import io.bisq.common.persistence.ListPersistable;
import io.bisq.common.persistence.LongPersistable;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.proto.PersistenceProtoResolver;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.trade.statistics.TradeStatistics;
import io.bisq.core.user.BlockChainExplorer;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.UserVO;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import io.bisq.network.p2p.storage.P2PDataStorage;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static io.bisq.core.proto.ProtoUtil.getPaymentAccount;

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

    @Inject
    public CoreDiskProtoResolver(Provider<Preferences> preferencesProvider,
                                 Provider<AddressEntryList> addressEntryListProvider
    ) {
        this.preferencesProvider = preferencesProvider;
        this.addressEntryListProvider = addressEntryListProvider;
    }

    @Override
    public Optional<Persistable> fromProto(PB.DiskEnvelope envelope) {
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
                /*
            case NAVIGATION:
                result = getPing(envelope);
                break;
                */
            case PEERS_LIST:
                result = getPeersList(envelope.getPeersList());
                break;

            case PREFERENCES:
                result = fillPreferences(envelope, preferencesProvider.get());
                break;
            case USER:
                result = UserVO.fromProto(envelope.getUser());
                break;
                /*
            case PERSISTED_P2P_STORAGE_DATA:
                result = getPing(envelope);
                break;
                */
            case SEQUENCE_NUMBER_MAP:
                result = getSequenceNumberMap(envelope.getSequenceNumberMap());
                break;
            case TRADE_STATISTICS_LIST:
                result = getTradeStatisticsList(envelope.getTradeStatisticsList());
            case BLOOM_FILTER_NONCE:
                result = getLongPersistable(envelope.getBloomFilterNonce());
                break;
            default:
                log.warn("Unknown message case:{}:{}", envelope.getMessageCase());
        }
        return Optional.ofNullable(result);
    }

    private Persistable getSequenceNumberMap(PB.SequenceNumberMap sequenceNumberMap) {
        Map<String, PB.MapValue> sequenceNumberMapMap = sequenceNumberMap.getSequenceNumberMapMap();
        HashMap<String, P2PDataStorage.MapValue> result = new HashMap<>();
        for(final Map.Entry<String, PB.MapValue> entry: sequenceNumberMapMap.entrySet()) {
            result.put(entry.getKey(), new P2PDataStorage.MapValue(entry.getValue().getSequenceNr(), entry.getValue().getTimeStamp()));
        }
        return new HashMapPersistable<>(result);
    }

    private Persistable getTradeStatisticsList(PB.TradeStatisticsList tradeStatisticsList) {
        return new ListPersistable<>(tradeStatisticsList.getTradeStatisticsList().stream()
                .map(tradeStatistics -> TradeStatistics.fromProto(tradeStatistics)).collect(Collectors.toList()));
    }

    private Persistable getPeersList(PB.PeersList envelope) {
        return new ListPersistable<>(envelope.getPeersList().stream().map(peer -> Peer.fromProto(peer))
                .collect(Collectors.toList()));
    }

    private Preferences fillPreferences(PB.DiskEnvelope envelope, Preferences preferences) {
        final PB.Preferences env = envelope.getPreferences();
        preferences.setUserLanguage(env.getUserLanguage());
        PB.Country userCountry = env.getUserCountry();
        preferences.setUserCountry(new Country(userCountry.getCode(), userCountry.getName(), new Region(userCountry.getRegion().getCode(), userCountry.getRegion().getName())));
        env.getFiatCurrenciesList().stream()
                .forEach(tradeCurrency -> preferences.addFiatCurrency((FiatCurrency) getTradeCurrency(tradeCurrency)));
        env.getCryptoCurrenciesList().stream()
                .forEach(tradeCurrency -> preferences.addCryptoCurrency((CryptoCurrency) getTradeCurrency(tradeCurrency)));
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
        preferences.setPreferredTradeCurrency(getTradeCurrency(preferredTradeCurrency));
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
        preferences.setSelectedPaymentAccountForCreateOffer(env.getSelectedPaymentAccountForCreateOffer().hasPaymentMethod() ? getPaymentAccount(env.getSelectedPaymentAccountForCreateOffer()) : null);

        preferences.setDoPersist(true);
        return preferences;
    }


    private TradeCurrency getTradeCurrency(PB.TradeCurrency tradeCurrency) {
        switch (tradeCurrency.getMessageCase()) {
            case FIAT_CURRENCY:
                return new FiatCurrency(tradeCurrency.getCode());
            case CRYPTO_CURRENCY:
                return new CryptoCurrency(tradeCurrency.getCode(), tradeCurrency.getName(), tradeCurrency.getSymbol(),
                        tradeCurrency.getCryptoCurrency().getIsAsset());
            default:
                log.warn("Unknown tradecurrency: {}", tradeCurrency.getMessageCase());
        }

        return null;
    }

    private Locale getLocale(PB.Locale locale) {
        return new Locale(locale.getLanguage(), locale.getCountry(), locale.getVariant());
    }

    private AddressEntryList fillAddressEntryList(PB.DiskEnvelope envelope, AddressEntryList addressEntryList) {
        envelope.getAddressEntryList().getAddressEntryList().stream().forEach(addressEntry -> {
            final AddressEntry entry = new AddressEntry(addressEntry.getPubKey().toByteArray(),
                    addressEntry.getPubKeyHash().toByteArray(),
                    AddressEntry.Context.valueOf(addressEntry.getContext().name()),
                    addressEntry.getOfferId(),
                    Coin.valueOf(addressEntry.getCoinLockedInMultiSig().getValue()));
            addressEntryList.addAddressEntry(entry);
        });
        addressEntryList.setDoPersist(true);
        return addressEntryList;
    }


    public LongPersistable getLongPersistable(PB.LongPersistable bloomFilterNonce) {
        return new LongPersistable(bloomFilterNonce.getLong());
    }
}
