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
import bisq.desktop.main.offer.bisq_v1.MutableOfferViewModel;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.BtcValidator;
import bisq.desktop.util.validation.FiatVolumeValidator;
import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.PriceUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.AltcoinValidator;
import bisq.core.util.validation.FiatPriceValidator;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

import javax.inject.Named;

class EditOfferViewModel extends MutableOfferViewModel<EditOfferDataModel> {

    @Inject
    public EditOfferViewModel(EditOfferDataModel dataModel,
                              FiatVolumeValidator fiatVolumeValidator,
                              FiatPriceValidator fiatPriceValidator,
                              AltcoinValidator altcoinValidator,
                              BtcValidator btcValidator,
                              BsqValidator bsqValidator,
                              SecurityDepositValidator securityDepositValidator,
                              PriceFeedService priceFeedService,
                              AccountAgeWitnessService accountAgeWitnessService,
                              Navigation navigation,
                              Preferences preferences,
                              @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                              BsqFormatter bsqFormatter,
                              OfferUtil offerUtil) {
        super(dataModel,
                fiatVolumeValidator,
                fiatPriceValidator,
                altcoinValidator,
                btcValidator,
                bsqValidator,
                securityDepositValidator,
                priceFeedService,
                accountAgeWitnessService,
                navigation,
                preferences,
                btcFormatter,
                bsqFormatter,
                offerUtil);
        syncMinAmountWithAmount = false;
    }

    @Override
    public void activate() {
        super.activate();

        dataModel.populateData();

        long triggerPriceAsLong = dataModel.getTriggerPrice();
        dataModel.setTriggerPrice(triggerPriceAsLong);
        if (triggerPriceAsLong > 0) {
            triggerPrice.set(PriceUtil.formatMarketPrice(triggerPriceAsLong, dataModel.getCurrencyCode()));
        } else {
            triggerPrice.set("");
        }
        onTriggerPriceTextFieldChanged();
    }

    public void applyOpenOffer(OpenOffer openOffer) {
        dataModel.reset();
        dataModel.applyOpenOffer(openOffer);
    }

    public void onStartEditOffer(ErrorMessageHandler errorMessageHandler) {
        dataModel.onStartEditOffer(errorMessageHandler);
    }

    public void onPublishOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onPublishOffer(resultHandler, errorMessageHandler);
    }

    public void onCancelEditOffer(ErrorMessageHandler errorMessageHandler) {
        dataModel.onCancelEditOffer(errorMessageHandler);
    }

    public void onInvalidateMarketPriceMargin() {
        marketPriceMargin.set(FormattingUtils.formatToPercent(dataModel.getMarketPriceMargin()));
    }

    public void onInvalidatePrice() {
        price.set(FormattingUtils.formatPrice(null));
        price.set(FormattingUtils.formatPrice(dataModel.getPrice().get()));
    }

    public boolean isSecurityDepositValid() {
        return securityDepositValidator.validate(buyerSecurityDeposit.get()).isValid;
    }

    @Override
    public void triggerFocusOutOnAmountFields() {
        // do not update BTC Amount or minAmount here
        // issue 2798: "after a few edits of offer the BTC amount has increased"
    }
}
