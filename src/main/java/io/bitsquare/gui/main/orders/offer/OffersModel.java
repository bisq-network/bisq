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

package io.bitsquare.gui.main.orders.offer;

import io.bitsquare.gui.UIModel;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import com.google.inject.Inject;

import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OffersModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(OffersModel.class);

    private final TradeManager tradeManager;
    private User user;

    private final ObservableList<OfferListItem> list = FXCollections.observableArrayList();
    private MapChangeListener<String, Offer> offerMapChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OffersModel(TradeManager tradeManager, User user) {
        this.tradeManager = tradeManager;
        this.user = user;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        offerMapChangeListener = change -> {
            if (change.wasAdded())
                list.add(new OfferListItem(change.getValueAdded()));
            else if (change.wasRemoved())
                list.removeIf(e -> e.getOffer().getId().equals(change.getValueRemoved().getId()));
        };

        super.initialize();
    }

    @Override
    public void activate() {
        super.activate();

        list.clear();
        list.addAll(tradeManager.getOffers().values().stream().map(OfferListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getOffer().getCreationDate().compareTo(o1.getOffer().getCreationDate()));

        tradeManager.getOffers().addListener(offerMapChangeListener);
    }

    @Override
    public void deactivate() {
        super.deactivate();

        tradeManager.getOffers().removeListener(offerMapChangeListener);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void removeOffer(OfferListItem item) {
        tradeManager.removeOffer(item.getOffer());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<OfferListItem> getList() {
        return list;
    }

    public Direction getDirection(Offer offer) {
        return offer.getMessagePublicKey().equals(user.getMessagePublicKey()) ?
                offer.getDirection() : offer.getMirroredDirection();
    }
}
