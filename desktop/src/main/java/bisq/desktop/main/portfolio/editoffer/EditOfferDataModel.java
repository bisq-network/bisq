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

package bisq.desktop.main.portfolio.editoffer;


import bisq.desktop.Navigation;
import bisq.desktop.main.offer.bisq_v1.MutableOfferDataModel;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.CreateOfferService;
import bisq.core.offer.bisq_v1.MutableOfferPayloadFields;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

import javax.inject.Named;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class EditOfferDataModel extends MutableOfferDataModel {

    private final CorePersistenceProtoResolver corePersistenceProtoResolver;
    private OpenOffer originalOpenOffer;
    private OpenOffer.State initialState;
    private Offer editedOffer;

    @Inject
    EditOfferDataModel(CreateOfferService createOfferService,
                       OpenOfferManager openOfferManager,
                       OfferUtil offerUtil,
                       BtcWalletService btcWalletService,
                       BsqWalletService bsqWalletService,
                       Preferences preferences,
                       User user,
                       P2PService p2PService,
                       PriceFeedService priceFeedService,
                       AccountAgeWitnessService accountAgeWitnessService,
                       FeeService feeService,
                       @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                       CorePersistenceProtoResolver corePersistenceProtoResolver,
                       TradeStatisticsManager tradeStatisticsManager,
                       Navigation navigation) {

        super(createOfferService,
                openOfferManager,
                offerUtil,
                btcWalletService,
                bsqWalletService,
                preferences,
                user,
                p2PService,
                priceFeedService,
                accountAgeWitnessService,
                feeService,
                btcFormatter,
                tradeStatisticsManager,
                navigation);
        this.corePersistenceProtoResolver = corePersistenceProtoResolver;
    }

    public void reset() {
        direction = null;
        tradeCurrency = null;
        tradeCurrencyCode.set(null);
        useMarketBasedPrice.set(false);
        amount.set(null);
        minAmount.set(null);
        price.set(null);
        volume.set(null);
        minVolume.set(null);
        buyerSecurityDeposit.set(0);
        paymentAccounts.clear();
        paymentAccount = null;
        marketPriceMargin = 0;
    }

    public void applyOpenOffer(OpenOffer openOffer) {
        this.originalOpenOffer = openOffer;

        Offer offer = openOffer.getOffer();
        direction = offer.getDirection();
        CurrencyUtil.getTradeCurrency(offer.getCurrencyCode())
                .ifPresent(c -> this.tradeCurrency = c);
        tradeCurrencyCode.set(offer.getCurrencyCode());

        this.initialState = openOffer.getState();
        PaymentAccount tmpPaymentAccount = user.getPaymentAccount(openOffer.getOffer().getMakerPaymentAccountId());
        Optional<TradeCurrency> optionalTradeCurrency = CurrencyUtil.getTradeCurrency(openOffer.getOffer().getCurrencyCode());
        if (optionalTradeCurrency.isPresent() && tmpPaymentAccount != null) {
            TradeCurrency selectedTradeCurrency = optionalTradeCurrency.get();
            this.paymentAccount = PaymentAccount.fromProto(tmpPaymentAccount.toProtoMessage(), corePersistenceProtoResolver);
            if (paymentAccount.getSingleTradeCurrency() != null)
                paymentAccount.setSingleTradeCurrency(selectedTradeCurrency);
            else
                paymentAccount.setSelectedTradeCurrency(selectedTradeCurrency);
        }

        // If the security deposit got bounded because it was below the coin amount limit, it can be bigger
        // by percentage than the restriction. We can't determine the percentage originally entered at offer
        // creation, so just use the default value as it doesn't matter anyway.
        double buyerSecurityDepositPercent = CoinUtil.getAsPercentPerBtc(offer.getBuyerSecurityDeposit(), offer.getAmount());
        if (buyerSecurityDepositPercent > Restrictions.getMaxBuyerSecurityDepositAsPercent()
                && offer.getBuyerSecurityDeposit().value == Restrictions.getMinBuyerSecurityDepositAsCoin().value)
            buyerSecurityDeposit.set(Restrictions.getDefaultBuyerSecurityDepositAsPercent());
        else
            buyerSecurityDeposit.set(buyerSecurityDepositPercent);

        allowAmountUpdate = false;
    }

    @Override
    public boolean initWithData(OfferDirection direction, TradeCurrency tradeCurrency) {
        try {
            return super.initWithData(direction, tradeCurrency);
        } catch (NullPointerException e) {
            if (e.getMessage().contains("tradeCurrency")) {
                throw new IllegalArgumentException("Offers of removed assets cannot be edited. You can only cancel it.", e);
            }
            return false;
        }
    }

    @Override
    protected Set<PaymentAccount> getUserPaymentAccounts() {
        return Objects.requireNonNull(user.getPaymentAccounts()).stream()
                .filter(account -> !account.getPaymentMethod().isBsqSwap())
                .collect(Collectors.toSet());
    }

    @Override
    protected PaymentAccount getPreselectedPaymentAccount() {
        return paymentAccount;
    }

    public void populateData() {
        Offer offer = originalOpenOffer.getOffer();
        // Min amount need to be set before amount as if minAmount is null it would be set by amount
        setMinAmount(offer.getMinAmount());
        setAmount(offer.getAmount());
        setPrice(offer.getPrice());
        setVolume(offer.getVolume());
        setUseMarketBasedPrice(offer.isUseMarketBasedPrice());
        setTriggerPrice(originalOpenOffer.getTriggerPrice());
        if (offer.isUseMarketBasedPrice()) {
            setMarketPriceMargin(offer.getMarketPriceMargin());
        }
    }

    public void onStartEditOffer(ErrorMessageHandler errorMessageHandler) {
        openOfferManager.editOpenOfferStart(originalOpenOffer, () -> {
        }, errorMessageHandler);
    }

    public void onPublishOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Offer offer = createAndGetOffer();
        if (offer.isBsqSwapOffer()) {
            return;
        }

        OfferPayload offerPayload = offer.getOfferPayload().orElseThrow();
        var mutableOfferPayloadFields = new MutableOfferPayloadFields(offerPayload);
        OfferPayload editedPayload = offerUtil.getMergedOfferPayload(originalOpenOffer, mutableOfferPayloadFields);
        editedOffer = new Offer(editedPayload);
        editedOffer.setPriceFeedService(priceFeedService);
        editedOffer.setState(Offer.State.AVAILABLE);
        openOfferManager.editOpenOfferPublish(editedOffer, triggerPrice, initialState, () -> {
            if (cannotActivateOffer()) {
                OpenOffer editedOpenOffer = openOfferManager.getOpenOfferById(editedOffer.getId()).orElseThrow();
                editedOpenOffer.setState(OpenOffer.State.DEACTIVATED);
            }
            resultHandler.handleResult();
            originalOpenOffer = null;
            editedOffer = null;
        }, errorMessageHandler);
    }

    public void onCancelEditOffer(ErrorMessageHandler errorMessageHandler) {
        if (originalOpenOffer != null)
            openOfferManager.editOpenOfferCancel(originalOpenOffer, initialState, () -> {
            }, errorMessageHandler);
    }

    public boolean cannotActivateOffer() {
        // The cannotActivateOffer check considers only activated offers but at editing offer we have set the
        // offer DEACTIVATED. We temporarily flip the state so that our cannotActivateOffer works as expected.
        originalOpenOffer.setState(OpenOffer.State.AVAILABLE);
        boolean result = openOfferManager.cannotActivateOffer(editedOffer);
        originalOpenOffer.setState(OpenOffer.State.DEACTIVATED);
        return result;
    }
}
