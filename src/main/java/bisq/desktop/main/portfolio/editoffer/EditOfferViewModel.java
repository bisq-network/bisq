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
import bisq.desktop.main.offer.MutableOfferViewModel;
import bisq.desktop.util.validation.AltcoinValidator;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.BtcValidator;
import bisq.desktop.util.validation.FiatPriceValidator;
import bisq.desktop.util.validation.FiatVolumeValidator;
import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.offer.OpenOffer;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

class EditOfferViewModel extends MutableOfferViewModel<EditOfferDataModel> {

    @Inject
    public EditOfferViewModel(EditOfferDataModel dataModel, FiatVolumeValidator fiatVolumeValidator, FiatPriceValidator fiatPriceValidator, AltcoinValidator altcoinValidator, BtcValidator btcValidator, BsqValidator bsqValidator, SecurityDepositValidator securityDepositValidator, P2PService p2PService, WalletsSetup walletsSetup, PriceFeedService priceFeedService, Navigation navigation, Preferences preferences, BSFormatter btcFormatter, BsqFormatter bsqFormatter) {
        super(dataModel, fiatVolumeValidator, fiatPriceValidator, altcoinValidator, btcValidator, bsqValidator, securityDepositValidator, p2PService, walletsSetup, priceFeedService, navigation, preferences, btcFormatter, bsqFormatter);
    }

    @Override
    public void activate() {
        super.activate();
        dataModel.populateData();
    }

    public void applyOpenOffer(OpenOffer openOffer) {
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
        marketPriceMargin.set("0.00%");
        marketPriceMargin.set(btcFormatter.formatToPercent(dataModel.getMarketPriceMargin()));
    }

    public void onInvalidatePrice() {
        price.set(btcFormatter.formatPrice(null));
        price.set(btcFormatter.formatPrice(dataModel.getPrice().get()));
    }
}
