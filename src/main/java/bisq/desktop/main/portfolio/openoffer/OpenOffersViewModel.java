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

package bisq.desktop.main.portfolio.openoffer;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.util.BSFormatter;

import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.locale.Res;
import bisq.common.monetary.Price;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

class OpenOffersViewModel extends ActivatableWithDataModel<OpenOffersDataModel> implements ViewModel {
    private final P2PService p2PService;
    final BSFormatter formatter;


    @Inject
    public OpenOffersViewModel(OpenOffersDataModel dataModel,
                               P2PService p2PService,
                               BSFormatter formatter) {
        super(dataModel);

        this.p2PService = p2PService;
        this.formatter = formatter;
    }

    void onActivateOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onActivateOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    void onDeactivateOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onDeactivateOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    void onRemoveOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onRemoveOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    public ObservableList<OpenOfferListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(OpenOfferListItem item) {
        return item.getOffer().getShortId();
    }

    String getAmount(OpenOfferListItem item) {
        return (item != null) ? formatter.formatAmount(item.getOffer()) : "";
    }

    String getPrice(OpenOfferListItem item) {
        if ((item == null))
            return "";

        Offer offer = item.getOffer();
        Price price = offer.getPrice();
        if (price != null) {
            String postFix = "";
            if (offer.isUseMarketBasedPrice())
                postFix = " (" + formatter.formatPercentagePrice(offer.getMarketPriceMargin()) + ")";
            return formatter.formatPrice(price) + postFix;
        } else {
            return Res.get("shared.na");
        }
    }

    String getVolume(OpenOfferListItem item) {
        return (item != null) ? formatter.formatVolume(item.getOffer(), false, 0) + " " + item.getOffer().getCurrencyCode() : "";
    }

    String getDirectionLabel(OpenOfferListItem item) {
        if ((item == null))
            return "";

        return formatter.getDirectionWithCode(dataModel.getDirection(item.getOffer()), item.getOffer().getCurrencyCode());
    }

    String getMarketLabel(OpenOfferListItem item) {
        if ((item == null))
            return "";

        return formatter.getCurrencyPair(item.getOffer().getCurrencyCode());
    }

    String getDate(OpenOfferListItem item) {
        return formatter.formatDateTime(item.getOffer().getDate());
    }

    boolean isDeactivated(OpenOfferListItem item) {
        return item != null && item.getOpenOffer() != null && item.getOpenOffer().isDeactivated();
    }

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }
}
