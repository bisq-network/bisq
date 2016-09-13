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

import com.google.inject.Inject;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class OpenOffersDataModel extends ActivatableDataModel {
    private final OpenOfferManager openOfferManager;
    private PriceFeedService priceFeedService;

    private final ObservableList<OpenOfferListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<OpenOffer> tradesListChangeListener;
    private ChangeListener<Number> currenciesUpdateFlagPropertyListener;

    @Inject
    public OpenOffersDataModel(OpenOfferManager openOfferManager, PriceFeedService priceFeedService) {
        this.openOfferManager = openOfferManager;
        this.priceFeedService = priceFeedService;

        tradesListChangeListener = change -> applyList();
        currenciesUpdateFlagPropertyListener = (observable, oldValue, newValue) -> applyList();
    }

    @Override
    protected void activate() {
        openOfferManager.getOpenOffers().addListener(tradesListChangeListener);
        priceFeedService.currenciesUpdateFlagProperty().addListener(currenciesUpdateFlagPropertyListener);
        applyList();
    }

    @Override
    protected void deactivate() {
        openOfferManager.getOpenOffers().removeListener(tradesListChangeListener);
        priceFeedService.currenciesUpdateFlagProperty().removeListener(currenciesUpdateFlagPropertyListener);
    }

    void onCancelOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        openOfferManager.removeOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }


    public ObservableList<OpenOfferListItem> getList() {
        return list;
    }

    public Offer.Direction getDirection(Offer offer) {
        return openOfferManager.isMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(openOfferManager.getOpenOffers().stream().map(OpenOfferListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getOffer().getDate().compareTo(o1.getOffer().getDate()));
    }


}
