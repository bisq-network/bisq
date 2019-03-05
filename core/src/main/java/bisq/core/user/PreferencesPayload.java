/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.user;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Country;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.proto.CoreProtoResolver;

import bisq.common.proto.ProtoUtil;
import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

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
    private double maxPriceDistanceInPercent = 0.3;
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

    @Deprecated // Superseded by buyerSecurityDepositAsPercent
    private long buyerSecurityDepositAsLong;

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
    @Nullable
    String referralId;
    @Nullable
    String phoneKeyAndToken;
    boolean useSoundForMobileNotifications = true;
    boolean useTradeNotifications = true;
    boolean useMarketNotifications = true;
    boolean usePriceNotifications = true;
    boolean useStandbyMode = false;
    boolean isDaoFullNode = false;
    @Nullable
    String rpcUser;
    @Nullable
    String rpcPw;
    @Nullable
    String takeOfferSelectedPaymentAccountId;
    private double buyerSecurityDepositAsPercent = Restrictions.getDefaultBuyerSecurityDepositAsPercent();


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
                .setBitcoinNodesOptionOrdinal(bitcoinNodesOptionOrdinal)
                .setUseSoundForMobileNotifications(useSoundForMobileNotifications)
                .setUseTradeNotifications(useTradeNotifications)
                .setUseMarketNotifications(useMarketNotifications)
                .setUsePriceNotifications(usePriceNotifications)
                .setUseStandbyMode(useStandbyMode)
                .setIsDaoFullNode(isDaoFullNode)
                .setBuyerSecurityDepositAsPercent(buyerSecurityDepositAsPercent);
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
        Optional.ofNullable(referralId).ifPresent(builder::setReferralId);
        Optional.ofNullable(phoneKeyAndToken).ifPresent(builder::setPhoneKeyAndToken);
        Optional.ofNullable(rpcUser).ifPresent(builder::setRpcUser);
        Optional.ofNullable(rpcPw).ifPresent(builder::setRpcPw);
        Optional.ofNullable(takeOfferSelectedPaymentAccountId).ifPresent(builder::setTakeOfferSelectedPaymentAccountId);

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
                proto.getBitcoinNodesOptionOrdinal(),
                proto.getReferralId().isEmpty() ? null : proto.getReferralId(),
                proto.getPhoneKeyAndToken().isEmpty() ? null : proto.getPhoneKeyAndToken(),
                proto.getUseSoundForMobileNotifications(),
                proto.getUseTradeNotifications(),
                proto.getUseMarketNotifications(),
                proto.getUsePriceNotifications(),
                proto.getUseStandbyMode(),
                proto.getIsDaoFullNode(),
                proto.getRpcUser().isEmpty() ? null : proto.getRpcUser(),
                proto.getRpcPw().isEmpty() ? null : proto.getRpcPw(),
                proto.getTakeOfferSelectedPaymentAccountId().isEmpty() ? null : proto.getTakeOfferSelectedPaymentAccountId(),
                proto.getBuyerSecurityDepositAsPercent());
    }
}
