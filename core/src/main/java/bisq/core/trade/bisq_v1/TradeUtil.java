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

package bisq.core.trade.bisq_v1;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Date;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.locale.CurrencyUtil.getCurrencyPair;
import static bisq.core.locale.CurrencyUtil.isFiatCurrency;
import static bisq.core.util.FormattingUtils.formatDurationAsWords;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * This class contains trade utility methods.
 */
@Slf4j
@Singleton
public class TradeUtil {

    // TODO change non-state dependent instance methods to static methods.

    private final BtcWalletService btcWalletService;
    private final KeyRing keyRing;

    @Inject
    public TradeUtil(BtcWalletService btcWalletService, KeyRing keyRing) {
        this.btcWalletService = btcWalletService;
        this.keyRing = keyRing;
    }

    /**
     * Returns <MULTI_SIG, TRADE_PAYOUT> if and only if both are AVAILABLE,
     * otherwise null.
     * @param trade the trade being queried for MULTI_SIG, TRADE_PAYOUT addresses
     * @return Tuple2 tuple containing MULTI_SIG, TRADE_PAYOUT addresses for trade
     */
    public Tuple2<String, String> getAvailableAddresses(Trade trade) {
        var addresses = getTradeAddresses(trade);
        if (addresses == null)
            return null;

        if (btcWalletService.getAvailableAddressEntries().stream()
                .noneMatch(e -> Objects.equals(e.getAddressString(), addresses.first)))
            return null;

        if (btcWalletService.getAvailableAddressEntries().stream()
                .noneMatch(e -> Objects.equals(e.getAddressString(), addresses.second)))
            return null;

        return new Tuple2<>(addresses.first, addresses.second);
    }

    /**
     * Returns <MULTI_SIG, TRADE_PAYOUT> addresses as strings if they're known by the
     * wallet.
     * @param trade the trade being queried for MULTI_SIG, TRADE_PAYOUT addresses
     * @return Tuple2 tuple containing MULTI_SIG, TRADE_PAYOUT addresses for trade
     */
    public Tuple2<String, String> getTradeAddresses(Trade trade) {
        var contract = trade.getContract();
        if (contract == null)
            return null;

        // Get multisig address
        var isMyRoleBuyer = contract.isMyRoleBuyer(keyRing.getPubKeyRing());
        var multiSigPubKey = isMyRoleBuyer
                ? contract.getBuyerMultiSigPubKey()
                : contract.getSellerMultiSigPubKey();
        if (multiSigPubKey == null)
            return null;

        var multiSigPubKeyString = Utilities.bytesAsHexString(multiSigPubKey);
        var multiSigAddress = btcWalletService.getAddressEntryListAsImmutableList().stream()
                .filter(e -> e.getKeyPair().getPublicKeyAsHex().equals(multiSigPubKeyString))
                .findAny()
                .orElse(null);
        if (multiSigAddress == null)
            return null;

        // Get payout address
        var payoutAddress = isMyRoleBuyer
                ? contract.getBuyerPayoutAddressString()
                : contract.getSellerPayoutAddressString();
        var payoutAddressEntry = btcWalletService.getAddressEntryListAsImmutableList().stream()
                .filter(e -> Objects.equals(e.getAddressString(), payoutAddress))
                .findAny()
                .orElse(null);
        if (payoutAddressEntry == null)
            return null;

        return new Tuple2<>(multiSigAddress.getAddressString(), payoutAddress);
    }

    public long getRemainingTradeDuration(Trade trade) {
        return trade.getMaxTradePeriodDate() != null
                ? trade.getMaxTradePeriodDate().getTime() - new Date().getTime()
                : getMaxTradePeriod(trade);
    }

    public long getMaxTradePeriod(Trade trade) {
        return trade.getOffer() != null
                ? trade.getOffer().getPaymentMethod().getMaxTradePeriod()
                : 0;
    }

    public double getRemainingTradeDurationAsPercentage(Trade trade) {
        long maxPeriod = getMaxTradePeriod(trade);
        long remaining = getRemainingTradeDuration(trade);
        if (maxPeriod != 0) {
            return 1 - (double) remaining / (double) maxPeriod;
        } else
            return 0;
    }

    public String getRemainingTradeDurationAsWords(Trade trade) {
        return formatDurationAsWords(Math.max(0, getRemainingTradeDuration(trade)));
    }

    @Nullable
    public Date getHalfTradePeriodDate(Trade trade) {
        return trade != null ? trade.getHalfTradePeriodDate() : null;
    }

    public Date getDateForOpenDispute(Trade trade) {
        return new Date(new Date().getTime() + getRemainingTradeDuration(trade));
    }

    public String getMarketDescription(Trade trade) {
        if (trade == null)
            return "";

        checkNotNull(trade.getOffer());
        checkNotNull(trade.getOffer().getCurrencyCode());
        return getCurrencyPair(trade.getOffer().getCurrencyCode());
    }

    /**
     * Returns a string describing a trader's role for a given trade.
     * @param trade Trade
     * @return String describing a trader's role for a given trade
     */
    public String getRole(Trade trade) {
        Contract contract = trade.getContract();
        if (contract == null)
            throw new IllegalStateException(format("could not get role because no contract was found for trade '%s'",
                    trade.getShortId()));

        Offer offer = trade.getOffer();
        if (offer == null)
            throw new IllegalStateException(format("could not get role because no offer was found for trade '%s'",
                    trade.getShortId()));

        return getRole(contract.isBuyerMakerAndSellerTaker(),
                offer.isMyOffer(keyRing),
                offer.getCurrencyCode());
    }

    /**
     * Returns a string describing a trader's role for a given bsq swap.
     * @param trade BsqSwapTrade
     * @return String describing a trader's role for a given bsq swap
     */
    public String getRole(BsqSwapTrade trade) {
        Offer offer = trade.getOffer();
        if (offer == null)
            throw new IllegalStateException(
                    format("could not get role because no offer was found for bsq swap '%s'",
                            trade.getShortId()));

        KeyRing keyRing = trade.getBsqSwapProtocolModel().getKeyRing();
        return getRole(offer.isBuyOffer(),
                offer.isMyOffer(keyRing),
                offer.getCurrencyCode());
    }

    /**
     * Returns a string describing a trader's role.
     *
     * @param isBuyerMakerAndSellerTaker boolean
     * @param isMaker boolean
     * @param currencyCode String
     * @return String describing a trader's role
     */
    public String getRole(boolean isBuyerMakerAndSellerTaker, boolean isMaker, String currencyCode) {
        if (isFiatCurrency(currencyCode)) {
            String baseCurrencyCode = Res.getBaseCurrencyCode();
            if (isBuyerMakerAndSellerTaker)
                return isMaker
                        ? Res.get("formatter.asMaker", baseCurrencyCode, Res.get("shared.buyer"))
                        : Res.get("formatter.asTaker", baseCurrencyCode, Res.get("shared.seller"));
            else
                return isMaker
                        ? Res.get("formatter.asMaker", baseCurrencyCode, Res.get("shared.seller"))
                        : Res.get("formatter.asTaker", baseCurrencyCode, Res.get("shared.buyer"));
        } else {
            if (isBuyerMakerAndSellerTaker)
                return isMaker
                        ? Res.get("formatter.asMaker", currencyCode, Res.get("shared.seller"))
                        : Res.get("formatter.asTaker", currencyCode, Res.get("shared.buyer"));
            else
                return isMaker
                        ? Res.get("formatter.asMaker", currencyCode, Res.get("shared.buyer"))
                        : Res.get("formatter.asTaker", currencyCode, Res.get("shared.seller"));
        }
    }

    public static void applyFilter(TradeModel tradeModel,
                                   FilterManager filterManager,
                                   NodeAddress nodeAddress,
                                   @Nullable PaymentAccountPayload paymentAccountPayload,
                                   ResultHandler complete,
                                   ErrorMessageHandler failed) {
        if (filterManager.isNodeAddressBanned(nodeAddress)) {
            failed.handleErrorMessage("Other trader is banned by their node address.\n" +
                    "tradingPeerNodeAddress=" + nodeAddress);
        } else if (filterManager.isOfferIdBanned(tradeModel.getId())) {
            failed.handleErrorMessage("Offer ID is banned.\n" + "Offer ID=" + tradeModel.getId());
        } else if (tradeModel.getOffer() != null &&
                filterManager.isCurrencyBanned(tradeModel.getOffer().getCurrencyCode())) {
            failed.handleErrorMessage("Currency is banned.\n" +
                    "Currency code=" + tradeModel.getOffer().getCurrencyCode());
        } else if (filterManager.isPaymentMethodBanned(checkNotNull(tradeModel.getOffer()).getPaymentMethod())) {
            failed.handleErrorMessage("Payment method is banned.\n" +
                    "Payment method=" + tradeModel.getOffer().getPaymentMethod().getId());
        } else if (paymentAccountPayload != null && filterManager.arePeersPaymentAccountDataBanned(paymentAccountPayload)) {
            failed.handleErrorMessage("Other trader is banned by their trading account data.\n" +
                    "paymentAccountPayload=" + paymentAccountPayload.getPaymentDetails());
        } else if (filterManager.requireUpdateToNewVersionForTrading()) {
            failed.handleErrorMessage("Your version of Bisq is not compatible for trading anymore. " +
                    "Please update to the latest Bisq version at https://bisq.network/downloads.");
        } else {
            complete.handleResult();
        }
    }
}
