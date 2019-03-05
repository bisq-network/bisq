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


import bisq.desktop.main.offer.MutableOfferDataModel;

import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;
import bisq.core.util.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

import java.util.Optional;

class EditOfferDataModel extends MutableOfferDataModel {

    private final CorePersistenceProtoResolver corePersistenceProtoResolver;
    private OpenOffer openOffer;
    private OpenOffer.State initialState;

    @Inject
    EditOfferDataModel(OpenOfferManager openOfferManager,
                       BtcWalletService btcWalletService,
                       BsqWalletService bsqWalletService,
                       Preferences preferences,
                       User user,
                       KeyRing keyRing,
                       P2PService p2PService,
                       PriceFeedService priceFeedService,
                       FilterManager filterManager,
                       AccountAgeWitnessService accountAgeWitnessService,
                       FeeService feeService,
                       TxFeeEstimationService txFeeEstimationService,
                       ReferralIdService referralIdService,
                       BSFormatter btcFormatter,
                       CorePersistenceProtoResolver corePersistenceProtoResolver) {
        super(openOfferManager,
                btcWalletService,
                bsqWalletService,
                preferences,
                user,
                keyRing,
                p2PService,
                priceFeedService,
                filterManager,
                accountAgeWitnessService,
                feeService,
                txFeeEstimationService,
                referralIdService,
                btcFormatter);
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
        buyerSecurityDeposit.set(0);
        paymentAccounts.clear();
        paymentAccount = null;
        marketPriceMargin = 0;
    }

    public void applyOpenOffer(OpenOffer openOffer) {
        this.openOffer = openOffer;

        Offer offer = openOffer.getOffer();
        direction = offer.getDirection();
        CurrencyUtil.getTradeCurrency(offer.getCurrencyCode())
                .ifPresent(c -> this.tradeCurrency = c);
        tradeCurrencyCode.set(offer.getCurrencyCode());
        buyerSecurityDeposit.set(CoinUtil.getAsPercentPerBtc(offer.getBuyerSecurityDeposit()));

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

        allowAmountUpdate = false;
    }

    @Override
    public boolean initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
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
    protected PaymentAccount getPreselectedPaymentAccount() {
        return paymentAccount;
    }

    public void populateData() {
        Offer offer = openOffer.getOffer();
        // Min amount need to be set before amount as if minAmount is null it would be set by amount
        setMinAmount(offer.getMinAmount());
        setAmount(offer.getAmount());
        setPrice(offer.getPrice());
        setVolume(offer.getVolume());
        setUseMarketBasedPrice(offer.isUseMarketBasedPrice());
        if (offer.isUseMarketBasedPrice()) setMarketPriceMargin(offer.getMarketPriceMargin());
    }

    public void onStartEditOffer(ErrorMessageHandler errorMessageHandler) {
        openOfferManager.editOpenOfferStart(openOffer, () -> {
        }, errorMessageHandler);
    }

    public void onPublishOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        final OfferPayload offerPayload = openOffer.getOffer().getOfferPayload();
        final OfferPayload editedPayload = new OfferPayload(offerPayload.getId(),
                offerPayload.getDate(),
                offerPayload.getOwnerNodeAddress(),
                offerPayload.getPubKeyRing(),
                offerPayload.getDirection(),
                getPrice().get().getValue(),
                getMarketPriceMargin(),
                isUseMarketBasedPriceValue(),
                getAmount().get().getValue(),
                getMinAmount().get().getValue(),
                offerPayload.getBaseCurrencyCode(),
                offerPayload.getCounterCurrencyCode(),
                offerPayload.getArbitratorNodeAddresses(),
                offerPayload.getMediatorNodeAddresses(),
                offerPayload.getPaymentMethodId(),
                offerPayload.getMakerPaymentAccountId(),
                offerPayload.getOfferFeePaymentTxId(),
                offerPayload.getCountryCode(),
                offerPayload.getAcceptedCountryCodes(),
                offerPayload.getBankId(),
                offerPayload.getAcceptedBankIds(),
                offerPayload.getVersionNr(),
                offerPayload.getBlockHeightAtOfferCreation(),
                offerPayload.getTxFee(),
                offerPayload.getMakerFee(),
                offerPayload.isCurrencyForMakerFeeBtc(),
                offerPayload.getBuyerSecurityDeposit(),
                offerPayload.getSellerSecurityDeposit(),
                offerPayload.getMaxTradeLimit(),
                offerPayload.getMaxTradePeriod(),
                offerPayload.isUseAutoClose(),
                offerPayload.isUseReOpenAfterAutoClose(),
                offerPayload.getLowerClosePrice(),
                offerPayload.getUpperClosePrice(),
                offerPayload.isPrivateOffer(),
                offerPayload.getHashOfChallenge(),
                offerPayload.getExtraDataMap(),
                offerPayload.getProtocolVersion());

        final Offer editedOffer = new Offer(editedPayload);
        editedOffer.setPriceFeedService(priceFeedService);
        editedOffer.setState(Offer.State.AVAILABLE);

        openOfferManager.editOpenOfferPublish(editedOffer, initialState, () -> {
            openOffer = null;
            resultHandler.handleResult();
        }, errorMessageHandler);
    }

    public void onCancelEditOffer(ErrorMessageHandler errorMessageHandler) {
        if (openOffer != null)
            openOfferManager.editOpenOfferCancel(openOffer, initialState, () -> {
            }, errorMessageHandler);
    }
}
