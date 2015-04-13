/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.portfolio.openoffer;

import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.offer.OpenOffer;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenOffersViewModel extends ActivatableWithDataModel<OpenOffersDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(OpenOffersViewModel.class);

    private final BSFormatter formatter;


    @Inject
    public OpenOffersViewModel(OpenOffersDataModel dataModel, BSFormatter formatter) {
        super(dataModel);

        this.formatter = formatter;
    }


    void onCancelOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onCancelOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    public ObservableList<OpenOfferListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(OpenOfferListItem item) {
        return item.getOffer().getId();
    }

    String getAmount(OpenOfferListItem item) {
        return (item != null) ? formatter.formatAmountWithMinAmount(item.getOffer()) : "";
    }

    String getPrice(OpenOfferListItem item) {
        return (item != null) ? formatter.formatFiat(item.getOffer().getPrice()) : "";
    }

    String getVolume(OpenOfferListItem item) {
        return (item != null) ? formatter.formatVolumeWithMinVolume(item.getOffer()) : "";
    }

    String getDirectionLabel(OpenOfferListItem item) {
        return (item != null) ? formatter.formatDirection(dataModel.getDirection(item.getOffer())) : "";
    }

    String getDate(OpenOfferListItem item) {
        return formatter.formatDateTime(item.getOffer().getCreationDate());
    }

}
