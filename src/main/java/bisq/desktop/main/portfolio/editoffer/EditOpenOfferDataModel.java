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


import bisq.desktop.main.offer.EditableOfferDataModel;
import bisq.desktop.util.BSFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

class EditOpenOfferDataModel extends EditableOfferDataModel {

    private OpenOffer openOffer;
    private OpenOffer.State initialState;

    @Inject
    EditOpenOfferDataModel(OpenOfferManager openOfferManager, BtcWalletService btcWalletService, BsqWalletService bsqWalletService, Preferences preferences, User user, KeyRing keyRing, P2PService p2PService, PriceFeedService priceFeedService, FilterManager filterManager, AccountAgeWitnessService accountAgeWitnessService, TradeWalletService tradeWalletService, FeeService feeService, BSFormatter formatter) {
        super(openOfferManager, btcWalletService, bsqWalletService, preferences, user, keyRing, p2PService, priceFeedService, filterManager, accountAgeWitnessService, tradeWalletService, feeService, formatter);
    }

    public void initWithData(OpenOffer openOffer) {
        this.openOffer = openOffer;
        this.initialState = openOffer.getState();
        this.paymentAccount = user.getPaymentAccount(openOffer.getOffer().getMakerPaymentAccountId());
    }

    public void populateData() {
        Offer offer = openOffer.getOffer();

        setAmount(offer.getAmount());
        setMinAmount(offer.getMinAmount());
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
