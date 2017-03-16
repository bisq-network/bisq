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
import io.bisq.gui.common.model.ActivatableDataModel;
import io.bisq.payload.trade.offer.OfferPayload;
import io.bisq.provider.price.PriceFeedService;
import io.bisq.trade.offer.Offer;
import io.bisq.trade.offer.OpenOffer;
import io.bisq.trade.offer.OpenOfferManager;
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

    public OfferPayload.Direction getDirection(Offer offer) {
        return openOfferManager.isMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(openOfferManager.getOpenOffers().stream().map(OpenOfferListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getOffer().getDate().compareTo(o1.getOffer().getDate()));
    }


}
