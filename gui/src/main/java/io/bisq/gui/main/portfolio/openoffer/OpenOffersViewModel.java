/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.portfolio.openoffer;

import com.google.inject.Inject;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.gui.common.model.ActivatableWithDataModel;
import io.bisq.gui.common.model.ViewModel;
import io.bisq.gui.util.BSFormatter;
import io.bisq.locale.Res;
import io.bisq.p2p.protocol.availability.Offer;
import io.bisq.p2p.storage.P2PService;
import io.bisq.trade.offer.OpenOffer;
import javafx.collections.ObservableList;
import org.bitcoinj.utils.Fiat;

class OpenOffersViewModel extends ActivatableWithDataModel<OpenOffersDataModel> implements ViewModel {
    private final P2PService p2PService;
    final BSFormatter formatter;


    @Inject
    public OpenOffersViewModel(OpenOffersDataModel dataModel, P2PService p2PService, BSFormatter formatter) {
        super(dataModel);

        this.p2PService = p2PService;
        this.formatter = formatter;
    }

    void onCancelOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onCancelOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    public ObservableList<OpenOfferListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(OpenOfferListItem item) {
        return item.getOffer().getShortId();
    }

    String getAmount(OpenOfferListItem item) {
        return (item != null) ? formatter.formatAmountWithMinAmount(item.getOffer()) : "";
    }

    String getPrice(OpenOfferListItem item) {
        if ((item == null))
            return "";

        Offer offer = item.getOffer();
        Fiat price = offer.getPrice();
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
        return (item != null) ? formatter.formatMinVolumeAndVolume(item.getOffer()) + " " + item.getOffer().getCurrencyCode() : "";
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

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }
}
