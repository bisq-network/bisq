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
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.model.DataModel;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;

import com.google.inject.Inject;

import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenOffersDataModel implements Activatable, DataModel {
    private static final Logger log = LoggerFactory.getLogger(OpenOffersDataModel.class);

    private final OpenOfferManager openOfferManager;

    private final ObservableList<OpenOfferListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<OpenOffer> tradesListChangeListener;

    @Inject
    public OpenOffersDataModel(OpenOfferManager openOfferManager) {
        this.openOfferManager = openOfferManager;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    public void activate() {
        applyList();
        openOfferManager.getOpenOffers().addListener(tradesListChangeListener);
    }

    @Override
    public void deactivate() {
        openOfferManager.getOpenOffers().removeListener(tradesListChangeListener);
    }

    void onCancelOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        openOfferManager.onCancelOpenOffer(openOffer, resultHandler, errorMessageHandler);
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
        list.sort((o1, o2) -> o2.getOffer().getCreationDate().compareTo(o1.getOffer().getCreationDate()));
    }
}
