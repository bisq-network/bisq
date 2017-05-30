package io.bisq.core.user;

import com.google.common.collect.Maps;
import com.google.protobuf.Message;
import io.bisq.common.locale.*;
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
    // getter is for the property
    private String btcDenomination = Preferences.BTC_DENOMINATIONS.get(0);
    // getter is for the property
    private boolean useAnimations;
    private final List<FiatCurrency> fiatCurrencies = new ArrayList<>();
    private final List<CryptoCurrency> cryptoCurrencies = new ArrayList<>();
    private BlockChainExplorer blockChainExplorerMainNet;
    private BlockChainExplorer blockChainExplorerTestNet;
    private BlockChainExplorer bsqBlockChainExplorer = new BlockChainExplorer("bisq", "https://explorer.bisq.io/tx.html?tx=",
            "https://explorer.bisq.io/Address.html?addr=");
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
    private boolean sortMarketCurrenciesNumerically = true;
    private boolean usePercentageBasedPrice = true;
    private Map<String, String> peerTagMap = new HashMap<>();
    private String bitcoinNodes = "";
    private List<String> ignoreTradersList = new ArrayList<>();
    private String directoryChooserPath;
    private long buyerSecurityDepositAsLong = Restrictions.DEFAULT_BUYER_SECURITY_DEPOSIT.value;
    @Nullable
    private PaymentAccount selectedPaymentAccountForCreateOffer;
    private boolean payFeeInBtc = true;
    private boolean resyncSpvRequested;

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
                .addAllFiatCurrencies(fiatCurrencies.stream().map(fiatCurrency -> ((PB.TradeCurrency) fiatCurrency.toProtoMessage())).collect(Collectors.toList()))
                .addAllCryptoCurrencies(cryptoCurrencies.stream().map(cryptoCurrency -> ((PB.TradeCurrency) cryptoCurrency.toProtoMessage())).collect(Collectors.toList()))
                .setBlockChainExplorerMainNet((PB.BlockChainExplorer) blockChainExplorerMainNet.toProtoMessage())
                .setBlockChainExplorerTestNet((PB.BlockChainExplorer) blockChainExplorerTestNet.toProtoMessage())
                .setBsqBlockChainExplorer((PB.BlockChainExplorer) bsqBlockChainExplorer.toProtoMessage())
                .setAutoSelectArbitrators(autoSelectArbitrators)
                .putAllDontShowAgainMap(dontShowAgainMap)
                .setTacAccepted(tacAccepted)
                .setUseAnimations(useAnimations)
                .setUseTorForBitcoinJ(useTorForBitcoinJ)
                .setShowOwnOffersInOfferBook(showOwnOffersInOfferBook)
                .setPreferredTradeCurrency((PB.TradeCurrency) preferredTradeCurrency.toProtoMessage())
                .setWithdrawalTxFeeInBytes(withdrawalTxFeeInBytes)
                .setMaxPriceDistanceInPercent(maxPriceDistanceInPercent)
                .setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumerically)
                .setUsePercentageBasedPrice(usePercentageBasedPrice)
                .setPayFeeInBtc(payFeeInBtc)
                .putAllPeerTagMap(peerTagMap)
                .setBitcoinNodes(bitcoinNodes)
                .addAllIgnoreTradersList(ignoreTradersList)
                .setDirectoryChooserPath(directoryChooserPath)
                .setBuyerSecurityDepositAsLong(buyerSecurityDepositAsLong);

        Optional.ofNullable(backupDirectory).ifPresent(backupDir -> builder.setBackupDirectory(backupDir));
        Optional.ofNullable(offerBookChartScreenCurrencyCode).ifPresent(code -> builder.setOfferBookChartScreenCurrencyCode(code));
        Optional.ofNullable(tradeChartsScreenCurrencyCode).ifPresent(code -> builder.setTradeChartsScreenCurrencyCode(code));
        Optional.ofNullable(buyScreenCurrencyCode).ifPresent(code -> builder.setBuyScreenCurrencyCode(code));
        Optional.ofNullable(sellScreenCurrencyCode).ifPresent(code -> builder.setSellScreenCurrencyCode(code));
        Optional.ofNullable(selectedPaymentAccountForCreateOffer).ifPresent(
                account -> builder.setSelectedPaymentAccountForCreateOffer(selectedPaymentAccountForCreateOffer.toProtoMessage()));
        return PB.PersistableEnvelope.newBuilder().setPreferencesPayload(builder).build();
    }

    public static PersistableEnvelope fromProto(PB.PreferencesPayload proto, CoreProtoResolver coreProtoResolver) {
        return new PreferencesPayload(
                proto.getUserLanguage(),
                new Country(proto.getUserCountry().getCode(),
                        proto.getUserCountry().getName(),
                        new Region(proto.getUserCountry().getRegion().getCode(), proto.getUserCountry().getRegion().getName())),
                proto.getBtcDenomination(),
                proto.getUseAnimations(),
                new BlockChainExplorer(proto.getBlockChainExplorerMainNet().getName(),
                        proto.getBlockChainExplorerMainNet().getTxUrl(),
                        proto.getBlockChainExplorerMainNet().getAddressUrl()),
                new BlockChainExplorer(proto.getBlockChainExplorerTestNet().getName(),
                        proto.getBlockChainExplorerTestNet().getTxUrl(),
                        proto.getBlockChainExplorerTestNet().getAddressUrl()),
                new BlockChainExplorer(proto.getBsqBlockChainExplorer().getName(),
                        proto.getBsqBlockChainExplorer().getTxUrl(),
                        proto.getBsqBlockChainExplorer().getAddressUrl()),
                ProtoUtil.emptyStringToNull(proto.getBackupDirectory()),
                proto.getAutoSelectArbitrators(),
                Maps.newHashMap(proto.getDontShowAgainMapMap()), // proto returns an unmodifiable map by default
                proto.getTacAccepted(),
                proto.getUseTorForBitcoinJ(),
                proto.getShowOwnOffersInOfferBook(),
                TradeCurrency.fromProto(proto.getPreferredTradeCurrency()),
                proto.getWithdrawalTxFeeInBytes(),
                proto.getUseCustomWithdrawalTxFee(),
                proto.getMaxPriceDistanceInPercent(),
                ProtoUtil.emptyStringToNull(proto.getOfferBookChartScreenCurrencyCode()),
                ProtoUtil.emptyStringToNull(proto.getTradeChartsScreenCurrencyCode()),
                ProtoUtil.emptyStringToNull(proto.getBuyScreenCurrencyCode()),
                ProtoUtil.emptyStringToNull(proto.getSellScreenCurrencyCode()),
                proto.getTradeStatisticsTickUnitIndex(),
                proto.getSortMarketCurrenciesNumerically(),
                proto.getUsePercentageBasedPrice(),
                proto.getPeerTagMapMap(),
                proto.getBitcoinNodes(),
                proto.getIgnoreTradersListList(),
                proto.getDirectoryChooserPath(),
                proto.getBuyerSecurityDepositAsLong(),
                proto.getSelectedPaymentAccountForCreateOffer().hasPaymentMethod() ?
                        PaymentAccount.fromProto(proto.getSelectedPaymentAccountForCreateOffer(), coreProtoResolver) :
                        null,
                proto.getPayFeeInBtc(),
                proto.getResyncSpvRequested());
    }
}
