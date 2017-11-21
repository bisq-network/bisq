package io.bisq.core.user;

import com.google.common.collect.Maps;
import com.google.protobuf.Message;
import io.bisq.common.locale.Country;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.generated.protobuffer.PB;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Data
@AllArgsConstructor
public final class PreferencesPayload implements PersistableEnvelope {
    private String userLanguage;
    private Country userCountry;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private List<FiatCurrency> fiatCurrencies = new ArrayList<>();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private List<CryptoCurrency> cryptoCurrencies = new ArrayList<>();
    private BlockChainExplorer blockChainExplorerMainNet;
    private BlockChainExplorer blockChainExplorerTestNet;
    private BlockChainExplorer bsqBlockChainExplorer = Preferences.BSQ_MAIN_NET_EXPLORER;
    @Nullable
    private String backupDirectory;
    private boolean autoSelectArbitrators = true;
    private Map<String, Boolean> dontShowAgainMap = new HashMap<>();
    private boolean tacAccepted;
    private boolean useTorForBitcoinJ = true;
    private boolean showOwnOffersInOfferBook = true;
    @Nullable
    private TradeCurrency preferredTradeCurrency;
    private long withdrawalTxFeeInBytes = 100;
    private boolean useCustomWithdrawalTxFee = false;
    private double maxPriceDistanceInPercent = 0.1;
    @Nullable
    private String offerBookChartScreenCurrencyCode;
    @Nullable
    private String tradeChartsScreenCurrencyCode;
    @Nullable
    private String buyScreenCurrencyCode;
    @Nullable
    private String sellScreenCurrencyCode;
    private int tradeStatisticsTickUnitIndex = 3;
    private boolean resyncSpvRequested;
    private boolean sortMarketCurrenciesNumerically = true;
    private boolean usePercentageBasedPrice = true;
    private Map<String, String> peerTagMap = new HashMap<>();
    // custom btc nodes
    private String bitcoinNodes = "";
    private List<String> ignoreTradersList = new ArrayList<>();
    private String directoryChooserPath;
    private long buyerSecurityDepositAsLong = Restrictions.getDefaultBuyerSecurityDeposit().value;
    private boolean useAnimations;
    @Nullable
    private PaymentAccount selectedPaymentAccountForCreateOffer;
    private boolean payFeeInBtc = true;
    @Nullable
    private List<String> bridgeAddresses;
    int bridgeOptionOrdinal;
    int torTransportOrdinal;
    @Nullable
    String customBridges;
    int bitcoinNodesOptionOrdinal;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PreferencesPayload() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        PB.PreferencesPayload.Builder builder = PB.PreferencesPayload.newBuilder()
                .setUserLanguage(userLanguage)
                .setUserCountry((PB.Country) userCountry.toProtoMessage())
                .addAllFiatCurrencies(fiatCurrencies.stream()
                        .map(fiatCurrency -> ((PB.TradeCurrency) fiatCurrency.toProtoMessage()))
                        .collect(Collectors.toList()))
                .addAllCryptoCurrencies(cryptoCurrencies.stream()
                        .map(cryptoCurrency -> ((PB.TradeCurrency) cryptoCurrency.toProtoMessage()))
                        .collect(Collectors.toList()))
                .setBlockChainExplorerMainNet((PB.BlockChainExplorer) blockChainExplorerMainNet.toProtoMessage())
                .setBlockChainExplorerTestNet((PB.BlockChainExplorer) blockChainExplorerTestNet.toProtoMessage())
                .setBsqBlockChainExplorer((PB.BlockChainExplorer) bsqBlockChainExplorer.toProtoMessage())
                .setAutoSelectArbitrators(autoSelectArbitrators)
                .putAllDontShowAgainMap(dontShowAgainMap)
                .setTacAccepted(tacAccepted)
                .setUseTorForBitcoinJ(useTorForBitcoinJ)
                .setShowOwnOffersInOfferBook(showOwnOffersInOfferBook)
                .setWithdrawalTxFeeInBytes(withdrawalTxFeeInBytes)
                .setUseCustomWithdrawalTxFee(useCustomWithdrawalTxFee)
                .setMaxPriceDistanceInPercent(maxPriceDistanceInPercent)
                .setTradeStatisticsTickUnitIndex(tradeStatisticsTickUnitIndex)
                .setResyncSpvRequested(resyncSpvRequested)
                .setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumerically)
                .setUsePercentageBasedPrice(usePercentageBasedPrice)
                .putAllPeerTagMap(peerTagMap)
                .setBitcoinNodes(bitcoinNodes)
                .addAllIgnoreTradersList(ignoreTradersList)
                .setDirectoryChooserPath(directoryChooserPath)
                .setBuyerSecurityDepositAsLong(buyerSecurityDepositAsLong)
                .setUseAnimations(useAnimations)
                .setPayFeeInBtc(payFeeInBtc)
                .setBridgeOptionOrdinal(bridgeOptionOrdinal)
                .setTorTransportOrdinal(torTransportOrdinal)
                .setBitcoinNodesOptionOrdinal(bitcoinNodesOptionOrdinal);
        Optional.ofNullable(backupDirectory).ifPresent(builder::setBackupDirectory);
        Optional.ofNullable(preferredTradeCurrency).ifPresent(e -> builder.setPreferredTradeCurrency((PB.TradeCurrency) e.toProtoMessage()));
        Optional.ofNullable(offerBookChartScreenCurrencyCode).ifPresent(builder::setOfferBookChartScreenCurrencyCode);
        Optional.ofNullable(tradeChartsScreenCurrencyCode).ifPresent(builder::setTradeChartsScreenCurrencyCode);
        Optional.ofNullable(buyScreenCurrencyCode).ifPresent(builder::setBuyScreenCurrencyCode);
        Optional.ofNullable(sellScreenCurrencyCode).ifPresent(builder::setSellScreenCurrencyCode);
        Optional.ofNullable(selectedPaymentAccountForCreateOffer).ifPresent(
                account -> builder.setSelectedPaymentAccountForCreateOffer(selectedPaymentAccountForCreateOffer.toProtoMessage()));
        Optional.ofNullable(bridgeAddresses).ifPresent(builder::addAllBridgeAddresses);
        Optional.ofNullable(customBridges).ifPresent(builder::setCustomBridges);
        return PB.PersistableEnvelope.newBuilder().setPreferencesPayload(builder).build();
    }

    public static PersistableEnvelope fromProto(PB.PreferencesPayload proto, CoreProtoResolver coreProtoResolver) {
        final PB.Country userCountry = proto.getUserCountry();
        PaymentAccount paymentAccount = null;
        if (proto.hasSelectedPaymentAccountForCreateOffer() && proto.getSelectedPaymentAccountForCreateOffer().hasPaymentMethod())
            paymentAccount = PaymentAccount.fromProto(proto.getSelectedPaymentAccountForCreateOffer(), coreProtoResolver);

        return new PreferencesPayload(
                proto.getUserLanguage(),
                Country.fromProto(userCountry),
                proto.getFiatCurrenciesList().isEmpty() ? new ArrayList<>() :
                        new ArrayList<>(proto.getFiatCurrenciesList().stream()
                                .map(FiatCurrency::fromProto)
                                .collect(Collectors.toList())),
                proto.getCryptoCurrenciesList().isEmpty() ? new ArrayList<>() :
                        new ArrayList<>(proto.getCryptoCurrenciesList().stream()
                                .map(CryptoCurrency::fromProto)
                                .collect(Collectors.toList())),
                BlockChainExplorer.fromProto(proto.getBlockChainExplorerMainNet()),
                BlockChainExplorer.fromProto(proto.getBlockChainExplorerTestNet()),
                BlockChainExplorer.fromProto(proto.getBsqBlockChainExplorer()),
                ProtoUtil.stringOrNullFromProto(proto.getBackupDirectory()),
                proto.getAutoSelectArbitrators(),
                Maps.newHashMap(proto.getDontShowAgainMapMap()),
                proto.getTacAccepted(),
                proto.getUseTorForBitcoinJ(),
                proto.getShowOwnOffersInOfferBook(),
                proto.hasPreferredTradeCurrency() ? TradeCurrency.fromProto(proto.getPreferredTradeCurrency()) : null,
                proto.getWithdrawalTxFeeInBytes(),
                proto.getUseCustomWithdrawalTxFee(),
                proto.getMaxPriceDistanceInPercent(),
                ProtoUtil.stringOrNullFromProto(proto.getOfferBookChartScreenCurrencyCode()),
                ProtoUtil.stringOrNullFromProto(proto.getTradeChartsScreenCurrencyCode()),
                ProtoUtil.stringOrNullFromProto(proto.getBuyScreenCurrencyCode()),
                ProtoUtil.stringOrNullFromProto(proto.getSellScreenCurrencyCode()),
                proto.getTradeStatisticsTickUnitIndex(),
                proto.getResyncSpvRequested(),
                proto.getSortMarketCurrenciesNumerically(),
                proto.getUsePercentageBasedPrice(),
                Maps.newHashMap(proto.getPeerTagMapMap()),
                proto.getBitcoinNodes(),
                proto.getIgnoreTradersListList(),
                proto.getDirectoryChooserPath(),
                proto.getBuyerSecurityDepositAsLong(),
                proto.getUseAnimations(),
                paymentAccount,
                proto.getPayFeeInBtc(),
                proto.getBridgeAddressesList().isEmpty() ? null : new ArrayList<>(proto.getBridgeAddressesList()),
                proto.getBridgeOptionOrdinal(),
                proto.getTorTransportOrdinal(),
                ProtoUtil.stringOrNullFromProto(proto.getCustomBridges()),
                proto.getBitcoinNodesOptionOrdinal());
    }
}
